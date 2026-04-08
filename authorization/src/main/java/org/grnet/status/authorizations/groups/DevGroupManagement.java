//package org.grnet.status.authorizations.groups;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.quarkus.arc.profile.IfBuildProfile;
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.inject.Inject;
//import org.grnet.status.authorizations.dtos.*;
//import org.jboss.logging.Logger;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//@ApplicationScoped
//@IfBuildProfile(anyOf = {"dev", "test"})
//public class DevGroupManagement implements GroupManagement {
//
//    @Inject
//    ObjectMapper objectMapper;
//
//    private static final Logger LOG = Logger.getLogger(DevGroupManagement.class);
//
//    @Override
//    public void createGroup(String parentPath, String name, List<String> roles, Map<String, List<String>> attributes) {
//        LOG.debugf("DEV: createGroup skipped (%s/%s)", parentPath, name);
//    }
//
//    @Override
//    public void deleteGroup(String fullGroupPath) {
//        LOG.debugf("DEV: deleteGroup skipped (%s)", fullGroupPath);
//    }
//
//    @Override
//    public GroupMembersResponse fetchGroupMembers(String fullPath, int first, int max, String search) {
//
//        LOG.debugf("DEV: fetchGroupMembers returns empty (%s)", fullPath);
//
//        String mockJson = """
//                {
//                              "results": [
//                                  {
//                                      "id": "e533bbd9-204b-4642-85e3-b6f9218c51ca",
//                                      "group": {
//                                          "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                          "name": "members",
//                                          "path": "/status-pages/members",
//                                          "attributes": {
//                                              "description": [
//                                                  "Members of status-page"
//                                              ],
//                                              "expiration-notification-period": [
//                                                  "21"
//                                              ],
//                                              "defaultConfiguration": [
//                                                  "a0aba987-9efb-4041-b683-312347ecb87b"
//                                              ]
//                                          }
//                                      },
//                                      "user": {
//                                          "id": "51552389-98b3-4567-b603-5046888ce1b7",
//                                          "username": "test@gmail.com",
//                                          "emailVerified": true,
//                                          "firstName": "Test",
//                                          "lastName": "Test",
//                                          "email": "test@gmail.com",
//                                          "attributes": {
//                                              "voPersonID": [
//                                                  "test@einfra.grnet.gr"
//                                              ],
//                                              "localEntitlements": [
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member"
//                                              ],
//                                              "cat_entitlements": [
//                                                  "assessment:add78a27-722c-402d-990a-548f1d3994f8",
//                                                  "assessment:2e1af1bd-1e92-49b2-a366-1623f4bde72d"
//                                              ],
//                                              "uid": [
//                                                   "test"
//                                              ]
//                                          },
//                                          "federatedIdentities": [
//                                              {
//                                                  "identityProvider": "google"
//                                              }
//                                          ]
//                                      },
//                                      "status": "ENABLED",
//                                      "validFrom": "2026-01-08",
//                                      "groupRoles": [
//                                          "member"
//                                      ],
//                                      "direct": true
//                                  },
//                                  {
//                                      "id": "64a75af0-7f54-4101-a9ea-907bee7da98e",
//                                      "group": {
//                                          "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                          "name": "members",
//                                          "path": "/status-pages/members",
//                                          "attributes": {
//                                              "description": [
//                                                  "Members of status-page"
//                                              ],
//                                              "expiration-notification-period": [
//                                                  "21"
//                                              ],
//                                              "defaultConfiguration": [
//                                                  "a0aba987-9efb-4041-b683-312347ecb87b"
//                                              ]
//                                          }
//                                      },
//                                      "user": {
//                                          "id": "6dd6a82f-baa7-4989-bcfa-741ebaa6cf8c",
//                                          "username": "test1@grnet-hq.admin.grnet.gr",
//                                          "emailVerified": true,
//                                          "firstName": "Test1",
//                                          "lastName": "Test1",
//                                          "email": "test1@admin.grnet.gr",
//                                          "attributes": {
//                                              "eduPersonAssurance": [
//                                                  "https://refeds.org/assurance",
//                                                  "https://refeds.org/assurance/IAP/medium",
//                                                  "https://refeds.org/assurance/ID/eppn-unique-no-reassign",
//                                                  "https://refeds.org/assurance/ID/unique",
//                                                  "https://refeds.org/assurance/IAP/low"
//                                              ],
//                                              "terms_and_conditions": [
//                                                  "1744118394"
//                                              ],
//                                              "voPersonID": [
//                                                  "test1@einfra.grnet.gr"
//                                              ],
//                                              "eduPersonScopedAffiliation": [
//                                                  "staff@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "localEntitlements": [
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:role=super_admin",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member"
//                                              ],
//                                              "displayName": [
//                                                  "Test1 Test1"
//                                              ],
//                                              "schacHomeOrganization": [
//                                                  "grnet.gr"
//                                              ],
//                                              "eduPersonPrincipalName": [
//                                                  "test1@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "cat_entitlements": [
//                                                  "assessment:ad5f3c93-bc3b-490b-a922-ae3594df1bd9"
//                                              ],
//                                              "uid": [
//                                                   "test1"
//                                              ]
//                                          },
//                                          "federatedIdentities": [
//                                              {
//                                                  "identityProvider": "National Infrastructures for Research and Technology - GRNET"
//                                              }
//                                          ]
//                                      },
//                                      "status": "ENABLED",
//                                      "validFrom": "2026-01-16",
//                                      "effectiveMembershipExpiresAt": "2026-11-25",
//                                      "effectiveGroupId": "47eb944b-9859-4bdc-97f1-337b95597e61",
//                                      "groupRoles": [
//                                          "member"
//                                      ],
//                                      "direct": true
//                                  },
//                                  {
//                                      "id": "68a915d5-57b2-4589-8581-544912fd5a4c",
//                                      "group": {
//                                          "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                          "name": "members",
//                                          "path": "/status-pages/members",
//                                          "attributes": {
//                                              "description": [
//                                                  "Members of status-page"
//                                              ],
//                                              "expiration-notification-period": [
//                                                  "21"
//                                              ],
//                                              "defaultConfiguration": [
//                                                  "a0aba987-9efb-4041-b683-312347ecb87b"
//                                              ]
//                                          }
//                                      },
//                                      "user": {
//                                          "id": "60d78c7f-7985-4553-9e51-6a18020b07db",
//                                          "username": "test2@grnet-hq.admin.grnet.gr",
//                                          "emailVerified": true,
//                                          "firstName": "Test2",
//                                          "lastName": "Test2",
//                                          "email": "test2@admin.grnet.gr",
//                                          "attributes": {
//                                              "eduPersonAssurance": [
//                                                  "https://refeds.org/assurance",
//                                                  "https://refeds.org/assurance/IAP/medium",
//                                                  "https://refeds.org/assurance/ID/eppn-unique-no-reassign",
//                                                  "https://refeds.org/assurance/ID/unique",
//                                                  "https://refeds.org/assurance/IAP/low"
//                                              ],
//                                              "terms_and_conditions": [
//                                                  "1752757234"
//                                              ],
//                                              "voPersonID": [
//                                                  "test2@einfra.grnet.gr"
//                                              ],
//                                              "eduPersonScopedAffiliation": [
//                                                  "staff@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "localEntitlements": [
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:tenants:TESTTENANT:role=admin",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:tenants:TESTTENANTINVITE:role=viewer",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member"
//                                              ],
//                                              "displayName": [
//                                                  "Test2 Test2"
//                                              ],
//                                              "schacHomeOrganization": [
//                                                  "grnet.gr"
//                                              ],
//                                              "eduPersonPrincipalName": [
//                                                  "test2@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "cat_entitlements": [
//                                                  "assessment:590c84ef-54cf-4b82-81c6-df3d4e295ac1",
//                                                  "assessment:b4b91b61-56d9-458f-9c5e-9ffdf50796eb",
//                                                  "assessment:b9cd2434-ac37-4414-9181-1a6f64bf2e71"
//                                              ],
//                                              "uid": [
//                                                   "test2"
//                                              ]
//                                          },
//                                          "federatedIdentities": [
//                                              {
//                                                  "identityProvider": "National Infrastructures for Research and Technology - GRNET"
//                                              }
//                                          ]
//                                      },
//                                      "status": "ENABLED",
//                                      "validFrom": "2025-12-17",
//                                      "groupRoles": [
//                                          "member"
//                                      ],
//                                      "direct": true
//                                  },
//                                  {
//                                      "id": "8e7a0537-1db3-4ecb-be9f-1470d4ed840e",
//                                      "group": {
//                                          "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                          "name": "members",
//                                          "path": "/status-pages/members",
//                                          "attributes": {
//                                              "description": [
//                                                  "Members of status-page"
//                                              ],
//                                              "expiration-notification-period": [
//                                                  "21"
//                                              ],
//                                              "defaultConfiguration": [
//                                                  "a0aba987-9efb-4041-b683-312347ecb87b"
//                                              ]
//                                          }
//                                      },
//                                      "user": {
//                                          "id": "beb70def-ff91-49b0-bce7-c23b762981fe",
//                                          "username": "test3@gmail.com",
//                                          "emailVerified": true,
//                                          "firstName": "Test3",
//                                          "lastName": "Test3",
//                                          "email": "test3@gmail.com",
//                                          "attributes": {
//                                              "terms_and_conditions": [
//                                                  "1750324712"
//                                              ],
//                                              "voPersonID": [
//                                                  "test3@einfra.grnet.gr"
//                                              ],
//                                              "localEntitlements": [
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:role=super_admin",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:tenants:TESTTENANTINVITE:role=viewer",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:tenants:TENANT%20TEST:role=admin"
//                                              ],
//                                              "cat_entitlements": [
//                                                  "assessment:52962555-7721-4d53-b768-9f8fdce2dad1",
//                                                  "assessment:a398415f-ff51-4a80-bcf7-eaa69784248b",
//                                                  "assessment:bf5dbfff-286d-4cba-83a4-b95e5b2adb8d",
//                                                  "assessment:59542713-6d3b-4ae9-918f-1d6f273d5ce4",
//                                                  "assessment:82d7d18a-1d15-480a-a9d7-df9e2f028605",
//                                                  "assessment:76c6d1fe-cb32-4058-b925-1c55952af4e4",
//                                                  "assessment:e88e57ce-5a4c-4ab4-ac73-525c2fe356c6",
//                                                  "assessment:a5dbca05-f4cc-4ab3-92d8-9c29b21c23fc",
//                                                  "assessment:eef82da0-8c93-489b-b02d-67c0a27e1bbd",
//                                                  "assessment:f7b4373e-190e-4f14-8268-faedc06acaff",
//                                                  "assessment:c53c34ab-320d-4102-9293-e3bfd8af09d7",
//                                                  "assessment:3d5d2bc9-c4e4-44bb-9cbe-fd7a321695c2",
//                                                  "assessment:9a8bfb3f-6362-4593-8e6d-c463f50752ac",
//                                                  "assessment:0670795b-5222-49a2-9bdf-22716f21f6e7",
//                                                  "assessment:603eb743-a904-4a67-9de3-a8207200026c",
//                                                  "assessment:1db92932-20b8-461f-bb7a-42f0e60c9059",
//                                                  "assessment:1f117eb9-1a9a-411e-a967-8a53d0c3f766",
//                                                  "assessment:b08d06ae-0c65-41b4-a51d-849838546a88",
//                                                  "assessment:7bf23d31-3d8e-4258-b1ee-46ee5e1fea53",
//                                                  "assessment:3d8e60fb-e7d4-4dab-aa99-eb92703a48c5",
//                                                  "assessment:fab8e9a4-7f12-4764-b923-d471145629b8",
//                                                  "assessment:19410765-c87e-4043-90a3-41607ebb610f",
//                                                  "assessment:05d8faf1-7a01-4c1b-9816-60ba0507f72c",
//                                                  "assessment:aa7d9dda-1b07-4b89-bc23-92cfd4b2173b",
//                                                  "assessment:57d1ae0f-3d25-4e7c-a387-85f049b53dbd",
//                                                  "assessment:b6de4536-6e75-41b7-9e35-12005428940c",
//                                                  "assessment:e024e1bf-ac2a-411c-8f56-672fdbe08103",
//                                                  "assessment:03ff1de9-9639-438f-befa-82dd199a2933",
//                                                  "assessment:347ebcd4-c404-4fe8-9564-151523254682",
//                                                  "assessment|8c903d27-6fec-4227-bb29-f27b2e0d7910",
//                                                  "assessment:61ccb5c6-a619-4bb7-9ac9-53a75f3e74bf",
//                                                  "assessment:3118d5b8-2c60-43cb-8e2d-5257531482d8",
//                                                  "assessment:70547933-f52c-4d2c-99b5-d98e0d4f2341"
//                                              ],
//                                              "uid": [
//                                                   "test3"
//                                              ]
//                                          },
//                                          "federatedIdentities": [
//                                              {
//                                                  "identityProvider": "google"
//                                              }
//                                          ]
//                                      },
//                                      "status": "ENABLED",
//                                      "validFrom": "2025-12-21",
//                                      "effectiveMembershipExpiresAt": "2026-11-11",
//                                      "effectiveGroupId": "47eb944b-9859-4bdc-97f1-337b95597e61",
//                                      "groupRoles": [
//                                          "member"
//                                      ],
//                                      "direct": true
//                                  },
//                                  {
//                                      "id": "e951c2c6-eb55-40cc-a8af-bc036eced292",
//                                      "group": {
//                                          "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                          "name": "members",
//                                          "path": "/status-pages/members",
//                                          "attributes": {
//                                              "description": [
//                                                  "Members of status-page"
//                                              ],
//                                              "expiration-notification-period": [
//                                                  "21"
//                                              ],
//                                              "defaultConfiguration": [
//                                                  "a0aba987-9efb-4041-b683-312347ecb87b"
//                                              ]
//                                          }
//                                      },
//                                      "user": {
//                                          "id": "1a29496e-5c35-43b2-a9d8-61b0283adce2",
//                                          "username": "test4@grnet-hq.admin.grnet.gr",
//                                          "emailVerified": true,
//                                          "firstName": "Test4",
//                                          "lastName": "Test4",
//                                          "email": "test4@admin.grnet.gr",
//                                          "attributes": {
//                                              "eduPersonAssurance": [
//                                                  "https://refeds.org/assurance",
//                                                  "https://refeds.org/assurance/IAP/medium",
//                                                  "https://refeds.org/assurance/ID/eppn-unique-no-reassign",
//                                                  "https://refeds.org/assurance/ID/unique",
//                                                  "https://refeds.org/assurance/IAP/low"
//                                              ],
//                                              "terms_and_conditions": [
//                                                  "1737555136"
//                                              ],
//                                              "voPersonID": [
//                                                  "test4@einfra.grnet.gr"
//                                              ],
//                                              "eduPersonScopedAffiliation": [
//                                                  "staff@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "localEntitlements": [
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:role=super_admin",
//                                                  "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member"
//                                              ],
//                                              "displayName": [
//                                                  "Test4 Test4"
//                                              ],
//                                              "schacHomeOrganization": [
//                                                  "grnet.gr"
//                                              ],
//                                              "eduPersonPrincipalName": [
//                                                  "test4@grnet-hq.admin.grnet.gr"
//                                              ],
//                                              "cat_entitlements": [
//                                                  "assessment:ffa726ca-36ad-4db1-8327-15c140a80ad9",
//                                                  "assessment:51e28c1a-e413-46f1-a1cd-37528df10533",
//                                                  "assessment:818e8ae7-744f-41cd-8214-6cb08def674c",
//                                                  "assessment:d19fe826-fda6-4201-858f-987f3fa78c5d",
//                                                  "assessment:334f5d5d-e423-4eaa-afd3-cc6f48acb646",
//                                                  "assessment:6e46951b-95fa-4f86-a79a-5708706e743b",
//                                                  "assessment:0ffa26d3-2871-44c1-812b-a7fd4648dcc2",
//                                                  "assessment:bf5dbfff-286d-4cba-83a4-b95e5b2adb8d",
//                                                  "assessment:c53c34ab-320d-4102-9293-e3bfd8af09d7",
//                                                  "assessment:f7b4373e-190e-4f14-8268-faedc06acaff",
//                                                  "assessment:eef82da0-8c93-489b-b02d-67c0a27e1bbd",
//                                                  "assessment:a5dbca05-f4cc-4ab3-92d8-9c29b21c23fc",
//                                                  "assessment:e88e57ce-5a4c-4ab4-ac73-525c2fe356c6",
//                                                  "assessment:971ed599-2b93-4de5-855d-d70cbbf87ffb",
//                                                  "assessment:c1805bdd-b475-499d-88de-1104012f9d47",
//                                                  "assessment:51c55819-e6b4-47b8-9435-335e1b4c06cc",
//                                                  "assessment:ea687a5b-6183-4e85-a1a6-23de737880ed",
//                                                  "assessment:f632fcc7-1d41-46b5-888f-5c49007285c1",
//                                                  "assessment:a398415f-ff51-4a80-bcf7-eaa69784248b",
//                                                  "assessment:a9590f60-1954-4eb6-9c4c-217e3ff3c07f",
//                                                  "assessment:b20fe5b8-b710-4428-9aad-b6b66a81e07b",
//                                                  "assessment:a37328c6-c225-4479-bf4b-6fbe1b3c72b0",
//                                                  "assessment:89b4a011-fb11-44c0-a8a2-9bbd72fce4f3",
//                                                  "assessment:e93f8f0d-0687-418d-a002-dcc877034cf4",
//                                                  "assessment:87af56c0-137b-485e-9fb7-3aca2870fdbb",
//                                                  "assessment:ac3be8c1-cb2d-45c5-95be-4247d76d45ff",
//                                                  "assessment:63f3e7e5-6c2d-4dba-9b5a-2d1f3c8d1eb9",
//                                                  "assessment:cfed9cf7-cf8b-4846-9467-824a62e21dee",
//                                                  "assessment:8b413c91-9f11-43bb-8b90-05f54dd57776",
//                                                  "assessment:b01caa0c-c851-4f87-a02f-f182209ea06f",
//                                                  "assessment:3eed7631-5661-4de8-9538-23fba99683c8",
//                                                  "assessment:309b64f0-eb67-4165-8e48-6c6325c3316f",
//                                                  "assessment:922420d0-2252-4e57-a66f-301090cd832d",
//                                                  "assessment:faa01508-725c-4a0f-952a-379eebaa6cb6",
//                                                  "assessment:0b75bed4-341c-4dbb-887f-31a580ac3f65",
//                                                  "assessment:50d0c7a5-8198-4119-9291-79faa3777e62",
//                                                  "assessment:c22ab839-b343-4508-bb7b-db8c496733f0",
//                                                  "assessment:78c05747-9eeb-4665-b28f-8909658148cc"
//                                              ],
//                                              "uid": [
//                                                   "test4"
//                                              ]
//                                          },
//                                          "federatedIdentities": [
//                                              {
//                                                  "identityProvider": "National Infrastructures for Research and Technology - GRNET"
//                                              }
//                                          ]
//                                      },
//                                      "status": "ENABLED",
//                                      "validFrom": "2026-01-20",
//                                      "effectiveMembershipExpiresAt": "2026-11-25",
//                                      "effectiveGroupId": "47eb944b-9859-4bdc-97f1-337b95597e61",
//                                      "groupRoles": [
//                                          "member"
//                                      ],
//                                      "direct": true
//                                  }
//                              ],
//                              "count": 5
//                          }
//        """;
//
//        try {
//            return objectMapper.readValue(mockJson, GroupMembersResponse.class);
//        } catch (Exception e) {
//           return new GroupMembersResponse();
//        }
//    }
//
//    @Override
//    public void addRole(String groupId, String role) {
//        LOG.debugf("DEV: addRole skipped (%s role=%s)", groupId, role);
//    }
//
//    @Override
//    public String getGroupId(String fullPath) {
//        LOG.debugf("DEV: getGroupId skipped (%s)", fullPath);
//        return null;
//    }
//
//    @Override
//    public void updateConfiguration(String groupId, List<String> groupRoles) {
//        LOG.debugf("DEV: updateConfiguration skipped (%s)", groupId);
//    }
//
//    @Override
//    public List<GroupUser> fetchGroupMembersByRole(String fullPath, String role) {
//        LOG.debugf("DEV: fetchGroupMembers returns empty (%s)", fullPath, role);
//        return List.of();
//    }
//
//    @Override
//    public void addGroupMember(String fullPath, String username, String role) {
//        LOG.debugf("DEV: addGroupMember skipped (user=%s, group=%s)", username, fullPath);
//    }
//
//    @Override
//    public void addMemberToGroupByGroupId(String id, String username, String role) {
//        LOG.debugf("DEV: addMemberToGroupByGroupId skipped (user=%s, group=%s)", username, id);
//    }
//
//    @Override
//    public List<PartialGroup> fetchGroups() {
//        LOG.debug("DEV: fetchGroups skipped");
//
//        String mockJson = """
//                {
//                            "results": [
//                                {
//                                    "id": "47eb944b-9859-4bdc-97f1-337b95597e61",
//                                    "name": "status-pages",
//                                    "path": "/status-pages",
//                                    "attributes": {
//                                        "description": [
//                                            "Status-pages API provides REST endpoints for creating, updating, and displaying service status pages, integrating with ARGO's monitoring reports and data sources."
//                                        ],
//                                        "defaultConfiguration": [
//                                            "de2dd406-fdbb-487e-985f-c0a802d5c370"
//                                        ]
//                                    },
//                                    "extraSubGroups": [
//                                        {
//                                            "id": "759e745e-bc94-4488-a452-c08e1ebe6fe8",
//                                            "name": "automation",
//                                            "path": "/status-pages/automation",
//                                            "attributes": {
//                                                "description": [
//                                                    "This group provides the required permissions for automated services that manage and report tenant lifecycle status events."
//                                                ],
//                                                "defaultConfiguration": [
//                                                    "90886028-c504-4adb-8120-c6770b0f46f6"
//                                                ]
//                                            },
//                                            "extraSubGroups": []
//                                        },
//                                        {
//                                            "id": "d1609083-213e-4f39-b9a4-28c1e66af604",
//                                            "name": "members",
//                                            "path": "/status-pages/members",
//                                            "groupRoles": {
//                                                        "member": "urn:mace:grnet.gr:einfra:login-devel:group:status-pages:members:role=member"
//                                            },
//                                            "attributes": {
//                                                "description": [
//                                                    "Members of status-page"
//                                                ],
//                                                "defaultConfiguration": [
//                                                    "a0aba987-9efb-4041-b683-312347ecb87b"
//                                                ]
//                                            },
//                                            "extraSubGroups": []
//                                        },
//                                        {
//                                            "id": "271f68a7-7c11-444e-8a04-82c2d6e70caa",
//                                            "name": "tenants",
//                                            "path": "/status-pages/tenants",
//                                            "attributes": {
//                                                "description": [
//                                                    "Tenants group of status page"
//                                                ],
//                                                "defaultConfiguration": [
//                                                    "2dcb49a8-5635-498b-a2f3-ab5a935336d5"
//                                                ]
//                                            },
//                                            "extraSubGroups": [
//                                                {
//                                                    "id": "fa5e636b-85b7-4c40-9184-2609b9f50cb3",
//                                                    "name": "EURODISCO",
//                                                    "path": "/status-pages/tenants/EURODISCO",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "9b43da5c-f5c6-467c-8e4b-db052abb7a16"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "e12d7f2c-67e7-464e-b21c-66c9fb2361e5"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "a84b7371-0977-4ef1-b70c-2ff39938ac30",
//                                                    "name": "EUROTRIP",
//                                                    "path": "/status-pages/tenants/EUROTRIP",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "889de5b8-a030-47bc-8148-4ecbea73d2fc"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "06b25133-86df-462c-acb6-0339c1846f9d"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "2311b9aa-f1a8-40da-b042-55b8940b3c9a",
//                                                    "name": "FAKETENANT",
//                                                    "path": "/status-pages/tenants/FAKETENANT",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "a3460ba5-b48e-4fef-9f03-0dd453088c10"
//                                                        ],
//                                                        "description": [
//                                                            "This is the GRNET tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "2ea578ea-9e37-4889-bbaf-9cf0b1b46e4e"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "f4f255ff-6af1-42a6-8060-72e9bed880a5",
//                                                    "name": "KAGGIS",
//                                                    "path": "/status-pages/tenants/KAGGIS",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "861cba4b-2c79-45b6-832b-20fe3198e0cb"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "66bf79d3-eb1d-4356-84f3-599e5aa78776"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "4497f2e2-5ebe-4b3f-8e48-b4d3781b3753",
//                                                    "name": "NINJATENANT",
//                                                    "path": "/status-pages/tenants/NINJATENANT",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "04262a48-a123-4497-8d6d-0459b0159c8a"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "f5ff2797-056d-4447-ae3d-eb8b87479c10"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "49901c41-f348-43be-978c-0cbe5867a0ee",
//                                                    "name": "SUPERTENANT",
//                                                    "path": "/status-pages/tenants/SUPERTENANT",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "30cf6921-771e-43a6-ba91-552928a19ad6"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "13554110-3ecc-4280-9705-b2086b4f3b69"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "fa1d8765-cbee-480e-9033-c6f8c7c658e1",
//                                                    "name": "TENANT TEST",
//                                                    "path": "/status-pages/tenants/TENANT TEST",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "e1ab046c-8544-47e6-bd8f-e8aa8b83acb3"
//                                                        ],
//                                                        "description": [
//                                                            "this is test tenant description"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "a5dbaabd-6342-4e50-acf0-b37b3ad8a9c5"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "6cb6e48b-2fac-4853-8d69-50b265c647ff",
//                                                    "name": "TENANTFOO",
//                                                    "path": "/status-pages/tenants/TENANTFOO",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "6ff3c0b9-4b6b-4c78-9ddd-2020d7300ab3"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "0afbda4b-0b4f-4cee-a6ec-7c0c53853052"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "e7973459-e9a8-4bf5-b445-f9359b553cbd",
//                                                    "name": "TENANTINITAUTOTEST",
//                                                    "path": "/status-pages/tenants/TENANTINITAUTOTEST",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "65cbd596-da2a-4ec7-8412-0528348c2cfd"
//                                                        ],
//                                                        "description": [
//                                                            "This is the GRNET tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "32cafe6d-0623-431e-95cb-e14ae4778bc2"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "027ca68a-c9da-4a62-bd59-033a9ca01ada",
//                                                    "name": "TESTING",
//                                                    "path": "/status-pages/tenants/TESTING",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "6145b6fc-06a9-4a09-b204-ddfc3123d0e2"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "e82671a0-b06f-4c00-8c27-36d3830c6366"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "e2db2bc2-381c-4d42-890c-df0c6c19d41b",
//                                                    "name": "TESTMANUALGROUP",
//                                                    "path": "/status-pages/tenants/TESTMANUALGROUP",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "2bf76a46-b50b-47f2-a549-4d5eb270f588"
//                                                        ],
//                                                        "description": [
//                                                            "This is the GRNET tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "54d02983-8b76-4d9f-920b-33ed753018f4"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "62b717ac-c8a6-4328-8b7c-d65c4d8118f4",
//                                                    "name": "TESTTENANT",
//                                                    "path": "/status-pages/tenants/TESTTENANT",
//                                                    "attributes": {
//                                                        "description": [
//                                                            "this is a test tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "15be324f-1680-4215-b650-0dca5521c5df"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "5c6a2a64-39ad-458f-b899-b817ed5bde55",
//                                                    "name": "TESTTENANTINVITE",
//                                                    "path": "/status-pages/tenants/TESTTENANTINVITE",
//                                                    "attributes": {
//                                                        "description": [
//                                                            "this is a test tenant invite"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "e4e9799d-70de-4a87-bec0-c7e38fc78e21"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "f3d25e9d-3261-4b51-b223-171d085a43af",
//                                                    "name": "THEMIS-TEST",
//                                                    "path": "/status-pages/tenants/THEMIS-TEST",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "1f449c34-5f80-4cb8-929c-799660f27756"
//                                                        ],
//                                                        "description": [
//                                                            "A test tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "33fa73fe-af41-4c78-bd76-22b1932a3271"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "54ca4bb5-a651-4e93-9340-7e56f9d9b23c",
//                                                    "name": "YOLO",
//                                                    "path": "/status-pages/tenants/YOLO",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "3d630d84-ac75-481d-a5c8-5fcc1a203e0d"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "864fa7b9-3487-4275-9964-08d56756c9ae"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "ae617715-0d3d-4bd6-bd81-5f1bea194e57",
//                                                    "name": "ZZZANOTHERTENANT",
//                                                    "path": "/status-pages/tenants/ZZZANOTHERTENANT",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "e738716f-793c-45de-94b1-3df50081b6d9"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "d331c5fd-df1e-4f04-a1df-48826c3b2088"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                },
//                                                {
//                                                    "id": "3784c6d1-432e-408f-ad87-f09b6e5c760a",
//                                                    "name": "ZZZTENANT",
//                                                    "path": "/status-pages/tenants/ZZZTENANT",
//                                                    "attributes": {
//                                                        "tenantId": [
//                                                            "967090e0-b625-429f-a17e-95660841f02b"
//                                                        ],
//                                                        "description": [
//                                                            "This is the a testing tenant"
//                                                        ],
//                                                        "defaultConfiguration": [
//                                                            "eb8bbb8d-830b-4f4c-9f05-23e740f6792a"
//                                                        ]
//                                                    },
//                                                    "extraSubGroups": []
//                                                }
//                                            ]
//                                        }
//                                    ]
//                                }
//                            ],
//                            "count": 1
//                        }
//        """;
//
//        try {
//            var mocks =  objectMapper.readValue(mockJson, GroupResponse.class);
//
//            var groups = new ArrayList<PartialGroup>();
//
//            for (Group group : mocks.results) {
//                collectGroupRecursive(group, groups);
//            }
//            return groups;
//
//        } catch (Exception e) {
//            return  List.of();
//        }
//    }
//
//    @Override
//    public void removeMemberFromGroup(String fullPath, String memberId) {
//        LOG.debugf("DEV: removeMemberFromGroup skipped (user=%s, group=%s)", memberId, fullPath);
//    }
//}