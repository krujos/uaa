package org.cloudfoundry.identity.uaa.util;

import org.cloudfoundry.identity.uaa.oauth.client.ClientDetailsModification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by pivotal on 9/21/15.
 */
public final class ClientUtils {

    private ClientUtils() { }

    // resourceId: "oauth"
    // scopes: "foo,bar"
    // grant_type: "client_credentials

    public static void createScimClient(MockMvc mockMvc, String adminAccessToken, String id, String secret, String resourceIds, String scopes, List<GrantType> grantTypes, String authorities) throws Exception {
        ClientDetailsModification client = new ClientDetailsModification(id, resourceIds, scopes, commaDelineatedGrantTypes(grantTypes), authorities);
        client.setClientSecret(secret);
        MockHttpServletRequestBuilder createClientPost = post("/oauth/clients")
            .header("Authorization", "Bearer " + adminAccessToken)
            .accept(APPLICATION_JSON)
            .contentType(APPLICATION_JSON)
            .content(JsonUtils.writeValueAsBytes(client));
        mockMvc.perform(createClientPost).andExpect(status().isCreated());
    }

    public enum GrantType {
        password, client_credentials, authorization_code, implicit
    }

    private static String commaDelineatedGrantTypes(List<GrantType> grantTypes) {
        StringBuilder grantTypeCommaDelineated = new StringBuilder();
        for (int i = 0; i < grantTypes.size(); i++) {
            if (i > 0) {
                grantTypeCommaDelineated.append(",");
            }
            grantTypeCommaDelineated.append(grantTypes.get(i).name());
        }
        return grantTypeCommaDelineated.toString();
    }
}
