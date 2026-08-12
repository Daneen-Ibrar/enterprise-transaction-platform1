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

    // Default credentials from environment (used as fallback)
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
            log.info("🔑 Using custom Xero credentials for tenant: {}", tenantId);
            return new XeroCredentials(token.getClientId(), token.getClientSecret());
        }

        if (DEFAULT_CLIENT_ID == null || DEFAULT_CLIENT_SECRET == null) {
            log.error("❌ No Xero credentials found for tenant: {}", tenantId);
            throw new IllegalStateException("Xero credentials not configured. Please connect your Xero account first.");
        }

        log.info("🔑 Using default Xero credentials for tenant: {}", tenantId);
        return new XeroCredentials(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET);
    }

    public String getAuthorizationUrlForTenant(Long tenantId) {
        XeroCredentials creds = getCredentialsForTenant(tenantId);
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

            log.info("🔍 Generated Xero auth URL for tenant: {}", tenantId);
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

        // ✅ FIX: Create Identity client with correct base path
        // Identity API uses https://api.xero.com (without /api.xro/2.0)
        ApiClient identityClient = new ApiClient("https://api.xero.com", null, null, null, null);
        IdentityApi idApi = new IdentityApi(identityClient);
        
        // ✅ Pass access token as parameter, NOT on the client
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

    public XeroToken getValidToken(Long tenantId) throws Exception {
        XeroToken token = tokenRepository.findValidTokenByTenantId(tenantId).orElse(null);

        if (token == null) {
            log.info("Token expired or not found, refreshing for tenant: {}", tenantId);
            token = refreshToken(tenantId);
        }

        return token;
    }

    @Transactional
    public void disconnect(Long tenantId) {
        tokenRepository.deleteByTenantId(tenantId);
        log.info("✅ Xero disconnected for tenant: {}", tenantId);
    }

    public boolean isConnected(Long tenantId) {
        return tokenRepository.findByTenantId(tenantId).isPresent();
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