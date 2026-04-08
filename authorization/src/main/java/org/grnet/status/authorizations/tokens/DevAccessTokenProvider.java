//package org.grnet.status.authorizations.tokens;
//
//import io.quarkus.arc.profile.IfBuildProfile;
//import jakarta.enterprise.context.ApplicationScoped;
//
//@ApplicationScoped
//@IfBuildProfile(anyOf = {"dev", "test"})
//public class DevAccessTokenProvider implements AccessTokenProvider {
//
//    @Override
//    public String getAccessToken() {
//        return "dev-token";
//    }
//}
