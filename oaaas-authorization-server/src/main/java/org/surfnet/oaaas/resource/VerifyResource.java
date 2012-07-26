/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.surfnet.oaaas.resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.surfnet.oaaas.model.AccessToken;
import org.surfnet.oaaas.model.ResourceServer;
import org.surfnet.oaaas.model.VerifyTokenResponse;
import org.surfnet.oaaas.repository.AccessTokenRepository;
import org.surfnet.oaaas.repository.ResourceServerRepository;

import com.sun.jersey.core.util.Base64;
import com.yammer.metrics.annotation.Timed;

/**
 * Resource for handling the call from resource servers to validate an access
 * token. As this is not part of the oauth2 <a
 * href="http://http://tools.ietf.org/html/draft-ietf-oauth-v2">spec</a>, we
 * have taken the Google <a href=
 * "https://developers.google.com/accounts/docs/OAuth2Login#validatingtoken"
 * >specification</a> as basis.
 * 
 */
@Named
@Path("/v1/tokeninfo")
@Produces(MediaType.APPLICATION_JSON)
public class VerifyResource {

  private static final Logger LOG = LoggerFactory.getLogger(VerifyResource.class);

  @Inject
  private AccessTokenRepository accessTokenRepository;

  @Inject
  private ResourceServerRepository resourceServerRepository;

  @GET
  @Timed
  public Response verifyToken(@HeaderParam(HttpHeaders.AUTHORIZATION)
  String authorization, @QueryParam("access_token")
  String accessToken) {
    if (authorization == null && accessToken == null) {
      return Response.status(Status.UNAUTHORIZED).build();
    }
    authorization = new String(Base64.decode(authorization));
    int atColon = authorization.indexOf(':');
    if (atColon < 1) {
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String name = authorization.substring(0, atColon);
    ResourceServer resourceServer = resourceServerRepository.findByName(name);
    if (resourceServer == null) {
      LOG.warn(String.format("ResourceServer(name='%s') not found", name));
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String secret = authorization.substring(atColon + 1);
    if (!resourceServer.getSecret().equals(secret)) {
      LOG.warn(String
          .format("ResourceServer(name='%s') is accessing VerifyResource#verifyToken with the wrong secret('%s')",
              name, secret));
      return Response.status(Status.UNAUTHORIZED).build();
    }
    AccessToken token = accessTokenRepository.findByToken(accessToken);
    if (token == null || (token.getExpires() != 0 && token.getExpires() < System.currentTimeMillis())) {
      return Response.status(Status.BAD_REQUEST).entity(new VerifyTokenResponse("invalid_token")).build();
    }
    return Response.ok(
        new VerifyTokenResponse(token.getClient().getName(), token.getScopes(), token.getPrincipal(), token
            .getExpires())).build();
  }

}