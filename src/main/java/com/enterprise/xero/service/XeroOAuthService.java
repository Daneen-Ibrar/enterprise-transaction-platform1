package com.enterprise.xero.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.enterprise.tenant.TenantContext;
import com.enterprise.xero.entity.XeroToken;
import com.enterprise.xero.repository.XeroTokenRepository;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.RefreshTokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.xero.api.ApiClient;
import com.xero.api.client.IdentityApi;
import com.xero.models.identity.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class XeroOAuthService {

    private static final Logger log = LoggerFactory.getLogger(XeroOAuthService.class);

    private final XeroTokenRepository tokenRepository;
    private final ApiClient defaultClient;
    private final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private final JsonFactory JSON_FACTORY = new JacksonFactory();
    private final String TOKEN_SERVER_URL = "https://identity.xero.com/connect/token";
    private final String AUTHORIZATION_SERVER_URL = "https://login.xero.com/identity/connect/authorize";

    private static String DEFAULT_CLIENT_ID;
    private static String DEFAULT_CLIENT_SECRET;

    public XeroOAuthService(XeroTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
        this.defaultClient = new ApiClient();
        DEFAULT_CLIENT_ID = System.getenv("XERO_CLIENT_ID");
        DEFAULT_CLIENT_SECRET = System.getenv("XERO_CLIENT_SECRET");
    }

    private XeroCredentials getCredentialsForTenant(Long tenantId) {
        XeroToken token = tokenRepository.findByTenantId(tenantId).orElse(null);
        
        if (token != null && token.getClientId() != null && token.getClientSecret() != null) {
            log.debug("🔑 Using custom Xero credentials for tenant: {}", tenantId);
            return new XeroCredentials(token.getClientId(), token.getClientSecret());
        }

        if (DEFAULT_CLIENT_ID == null || DEFAULT_CLIENT_SECRET == null) {
            log.warn("⚠️ No default Xero credentials found for tenant: {}", tenantId);
            return null;
        }

        log.debug("🔑 Using default Xero credentials for tenant: {}", tenantId);
        return new XeroCredentials(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET);
    }

    public String getAuthorizationUrlForTenant(Long tenantId) {
        XeroCredentials creds = getCredentialsForTenant(tenantId);
        if (creds == null) {
            throw new IllegalStateException("No Xero credentials configured for tenant " + tenantId);
        }
        
        String secretState = "secret" + new Random().nextInt(999_999);
        ArrayList<String> scopeList = getScopes();

        try {
            AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
                    BearerToken.authorizationHeaderAccessMethod(),
                    HTTP_TRANSPORT,
                    JSON_FACTORY,
                    new GenericUrl(TOKEN_SERVER_URL),
                    new ClientParametersAuthentication(creds.clientId, creds.clientSecret),
                    creds.clientId,
                    AUTHORIZATION_SERVER_URL)
                    .setScopes(scopeList)
                    .build();

            String redirectUri = getRedirectUri();
            String url = flow.newAuthorizationUrl()
                    .setClientId(creds.clientId)
                    .setScopes(scopeList)
                    .setState(secretState)
                    .setRedirectUri(redirectUri)
                    .build();

            log.debug("🔍 Generated Xero auth URL for tenant: {}", tenantId);
            return url;

        } catch (Exception e) {
            log.error("Failed to generate authorization URL for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to generate Xero authorization URL", e);
        }
    }

    @Transactional
    public XeroToken exchangeCodeForTokens(Long tenantId, String code, String state) throws Exception {
        log.info("🔄 Exchanging code for tokens for tenant: {}", tenantId);

        XeroCredentials creds = getCredentialsForTenant(tenantId);
        if (creds == null) {
            throw new IllegalStateException("No Xero credentials configured for tenant " + tenantId);
        }
        
        ArrayList<String> scopeList = getScopes();

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT,
                JSON_FACTORY,
                new GenericUrl(TOKEN_SERVER_URL),
                new ClientParametersAuthentication(creds.clientId, creds.clientSecret),
                creds.clientId,
                AUTHORIZATION_SERVER_URL)
                .setScopes(scopeList)
                .build();

        String redirectUri = getRedirectUri();
        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        DecodedJWT verifiedJWT = defaultClient.verify(tokenResponse.getAccessToken());

        ApiClient identityClient = new ApiClient("https://api.xero.com", null, null, null, null);
        IdentityApi idApi = new IdentityApi(identityClient);
        
        List<Connection> connections = idApi.getConnections(tokenResponse.getAccessToken(), null);

        if (connections == null || connections.isEmpty()) {
            throw new RuntimeException("No Xero organisations found for this account");
        }

        String xeroTenantId = connections.get(0).getTenantId().toString();
        log.info("✅ Xero tenant ID: {}", xeroTenantId);

        XeroToken token = tokenRepository.findByTenantId(tenantId).orElse(new XeroToken());
        token.setAccessToken(verifiedJWT.getToken());
        token.setRefreshToken(tokenResponse.getRefreshToken());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
        token.setXeroTenantId(xeroTenantId);
        token.setTenantId(tenantId);
        token.setClientId(creds.clientId);
        token.setClientSecret(creds.clientSecret);
        token.setUpdatedAt(LocalDateTime.now());

        XeroToken saved = tokenRepository.save(token);
        log.info("✅ Xero token saved for tenant: {}", tenantId);
        return saved;
    }

    @Transactional
    public XeroToken refreshToken(Long tenantId) throws Exception {
        log.info("🔄 Refreshing Xero token for tenant: {}", tenantId);

        XeroToken existing = tokenRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No Xero token found for tenant: " + tenantId));

        XeroCredentials creds = getCredentialsForTenant(tenantId);
        if (creds == null) {
            throw new IllegalStateException("No Xero credentials configured for tenant " + tenantId);
        }

        try {
            TokenResponse tokenResponse = new RefreshTokenRequest(
                    new NetHttpTransport(),
                    new JacksonFactory(),
                    new GenericUrl(TOKEN_SERVER_URL),
                    existing.getRefreshToken())
                    .setClientAuthentication(new BasicAuthentication(creds.clientId, creds.clientSecret))
                    .execute();

            DecodedJWT verifiedJWT = defaultClient.verify(tokenResponse.getAccessToken());

            existing.setAccessToken(verifiedJWT.getToken());
            existing.setRefreshToken(tokenResponse.getRefreshToken());
            existing.setExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            existing.setUpdatedAt(LocalDateTime.now());

            log.info("✅ Xero token refreshed for tenant: {}", tenantId);
            return tokenRepository.save(existing);

        } catch (TokenResponseException e) {
            log.error("Token refresh failed for tenant {}: {}", tenantId, e.getMessage());
            throw e;
        }
    }

    // ============================================================
    // ✅ FIXED: Returns null instead of throwing
    // ============================================================
    public XeroToken getValidToken(Long tenantId) {
        try {
            XeroToken token = tokenRepository.findValidTokenByTenantId(tenantId).orElse(null);

            if (token == null) {
                log.info("Token expired or not found, attempting refresh for tenant: {}", tenantId);
                token = refreshToken(tenantId);
            }

            return token;
        } catch (Exception e) {
            log.warn("⚠️ Could not get valid Xero token for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    @Transactional
    public void disconnect(Long tenantId) {
        tokenRepository.deleteByTenantId(tenantId);
        log.info("✅ Xero disconnected for tenant: {}", tenantId);
    }

    public boolean isConnected(Long tenantId) {
        try {
            return tokenRepository.findByTenantId(tenantId).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public List<XeroToken> getAllConnectedTenants() {
        return tokenRepository.findAll();
    }

    public boolean hasCustomCredentials(Long tenantId) {
        XeroToken token = tokenRepository.findByTenantId(tenantId).orElse(null);
        return token != null && token.getClientId() != null && token.getClientSecret() != null;
    }

    private ArrayList<String> getScopes() {
        ArrayList<String> scopeList = new ArrayList<>();
        scopeList.add("openid");
        scopeList.add("email");
        scopeList.add("profile");
        scopeList.add("offline_access");
        scopeList.add("accounting.settings");
        scopeList.add("accounting.contacts");
        scopeList.add("accounting.attachments");
        scopeList.add("accounting.payments");
        scopeList.add("accounting.invoices");
        scopeList.add("accounting.banktransactions");
        scopeList.add("accounting.manualjournals");
        scopeList.add("accounting.reports.balancesheet.read");
        scopeList.add("accounting.reports.profitandloss.read");
        return scopeList;
    }

    private String getRedirectUri() {
        String redirectUri = System.getenv("XERO_REDIRECT_URI");
        if (redirectUri == null) {
            redirectUri = "https://fluffy-trout-9pprj57j7xpf75pq-8080.app.github.dev/admin/xero/callback";
        }
        return redirectUri;
    }

    private static class XeroCredentials {
        final String clientId;
        final String clientSecret;

        XeroCredentials(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }
}