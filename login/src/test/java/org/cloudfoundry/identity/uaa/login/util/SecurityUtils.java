package org.cloudfoundry.identity.uaa.login.util;

import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Created by pivotal on 9/18/15.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static SecurityContext defaultSecurityContext(Authentication authentication) {
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }

    public static Authentication fullyAuthenticatedUser(String id, String username, String email, GrantedAuthority... authorities) {
        UaaPrincipal p = new UaaPrincipal(id, username, email, Origin.UAA,"", IdentityZoneHolder.get().getId());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, "", Arrays.asList(authorities));
        assertTrue(auth.isAuthenticated());
        return auth;
    }

   public static Authentication oauthAuthenticatedClient(String clientId, Set<String> scopes, Set<GrantedAuthority> authorities) {
        OAuth2Authentication auth = new OAuth2Authentication(new OAuth2Request(null, clientId, authorities, true, scopes, null, null, null, null), null);
        assertTrue(auth.isAuthenticated());
        return auth;
   }
}
