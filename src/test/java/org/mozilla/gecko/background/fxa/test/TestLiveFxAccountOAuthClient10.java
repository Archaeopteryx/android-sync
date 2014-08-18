/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.gecko.background.fxa.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mozilla.android.sync.test.integration.IntegrationTestCategory;
import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.background.fxa.FxAccountClient20;
import org.mozilla.gecko.background.fxa.FxAccountClient20.LoginResponse;
import org.mozilla.gecko.background.fxa.FxAccountClientException.FxAccountClientRemoteException;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.background.fxa.oauth.FxAccountAbstractClient;
import org.mozilla.gecko.background.fxa.oauth.FxAccountAbstractClient.RequestDelegate;
import org.mozilla.gecko.background.fxa.oauth.FxAccountAbstractClientException.FxAccountAbstractClientRemoteException;
import org.mozilla.gecko.background.fxa.oauth.FxAccountOAuthClient10;
import org.mozilla.gecko.background.fxa.oauth.FxAccountOAuthClient10.AuthorizationResponse;
import org.mozilla.gecko.background.fxa.profile.FxAccountProfileClient10;
import org.mozilla.gecko.background.testhelpers.WaitHelper;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.JSONWebTokenUtils;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.net.BaseResource;

@Category(IntegrationTestCategory.class)
public class TestLiveFxAccountOAuthClient10 {
  // protected static final String TEST_SERVERURI = "http://127.0.0.1:9010/v1";
  protected static final String TEST_SERVERURI = "https://oauth-latest.dev.lcip.org/v1";

  protected static final String TEST_AUTH_SERVERURI = "https://latest.dev.lcip.org/auth/v1";

  protected static final String TEST_PROFILE_SERVERURI = "https://latest.dev.lcip.org/profile/v1";

  protected BrowserIDKeyPair keyPair;

  public ExecutorService executor;
  public FxAccountOAuthClient10 client;
  public FxAccountClient20 fxaClient;

  public FxAccountProfileClient10 profileClient;

  @Before
  public void setUp() throws Exception {
    Logger.startLoggingToConsole();
    BaseResource.rewriteLocalhost = false;

    if (keyPair == null) {
      keyPair = RSACryptoImplementation.generateKeyPair(1024);
    }

    executor = Executors.newSingleThreadExecutor();
    client = new FxAccountOAuthClient10(TEST_SERVERURI, executor);
    fxaClient = new FxAccountClient20(TEST_AUTH_SERVERURI, executor);

    profileClient = new FxAccountProfileClient10(TEST_PROFILE_SERVERURI, executor);
  }

  @Test
  public void testAuthorization() throws Throwable {
    final String TEST_EMAIL = "testtesto@mockmyid.com";
    final String TEST_PASSWORD = "testtesto@mockmyid.com";
    final String TEST_SCOPE = "profile";
    final String TEST_AUDIENCE = FxAccountUtils.getAudienceForURL(TEST_SERVERURI);

    // final String TEST_CLIENT_ID = "98e6508e88680e1a"; // Localhost, canGrant = true.
    // final String TEST_CLIENT_ID = "dcdb5ae7add825d2"; // Localhost, canGrant = false.
    final String TEST_CLIENT_ID = "0fddc2b28f47c2d8"; // Remote, canGrant = true.

    try {
      LoginResponse createResponse = TestLiveFxAccountClient20.createAccount(fxaClient, TEST_EMAIL, TEST_PASSWORD, false, TestLiveFxAccountClient20.VerificationState.UNVERIFIED);
      Assert.assertNotNull(createResponse.uid);
      Assert.assertNotNull(createResponse.sessionToken);
    } catch (FxAccountClientRemoteException e) {
      if (!(e.isAccountAlreadyExists() || e.isTooManyRequests())) {
        throw e;
      }
    }

    LoginResponse loginResponse = TestLiveFxAccountClient20.login(fxaClient, TEST_EMAIL, TEST_PASSWORD, false);
    byte[] sessionToken = loginResponse.sessionToken;

    String certificate = TestLiveFxAccountClient20.certificateSign(fxaClient, keyPair.getPublic().toJSONObject(), 24*60*60*1000, sessionToken);

    final long expiresAt = JSONWebTokenUtils.DEFAULT_FUTURE_EXPIRES_AT_IN_MILLISECONDS;
    final String assertion = JSONWebTokenUtils.createAssertion(keyPair.getPrivate(), certificate, TEST_AUDIENCE, JSONWebTokenUtils.DEFAULT_ASSERTION_ISSUER, null, expiresAt);
    JSONWebTokenUtils.dumpAssertion(assertion);

    final AuthorizationResponse authorization = doAuthorization(TEST_CLIENT_ID, TEST_SCOPE, assertion);

    Assert.assertNotNull(authorization);
    Assert.assertNotNull(authorization.scope);
    Assert.assertNotNull(authorization.access_token);

    final ExtendedJSONObject profile = doProfile(authorization.access_token);

    Assert.assertNotNull(profile);
    Assert.assertEquals(TEST_EMAIL, profile.getString("email"));
  }

  public AuthorizationResponse doAuthorization(final String client_id, final String scope, final String assertion) {
    final AuthorizationResponse[] results = new AuthorizationResponse[1];

    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        // XXX Watch the argument order.
        client.authorization(client_id, assertion, null, scope, new RequestDelegate<FxAccountOAuthClient10.AuthorizationResponse>() {
          @Override
          public void handleSuccess(AuthorizationResponse result) {
            results[0] = result;
            WaitHelper.getTestWaiter().performNotify();
          }

          @Override
          public void handleFailure(FxAccountAbstractClientRemoteException e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }

          @Override
          public void handleError(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }
        });
      }
    });

    return results[0];
  }

  public ExtendedJSONObject doProfile(final String token) {
    final ExtendedJSONObject[] results = new ExtendedJSONObject[1];

    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        profileClient.profile(token, new FxAccountAbstractClient.RequestDelegate<ExtendedJSONObject>() {
          @Override
          public void handleSuccess(ExtendedJSONObject result) {
            results[0] = result;
            WaitHelper.getTestWaiter().performNotify();
          }

          @Override
          public void handleFailure(FxAccountAbstractClientRemoteException e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }

          @Override
          public void handleError(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }
        });
      }
    });

    return results[0];
  }
}
