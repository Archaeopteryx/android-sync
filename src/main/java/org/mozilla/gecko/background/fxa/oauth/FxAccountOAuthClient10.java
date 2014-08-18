/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.background.fxa.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executor;

import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.net.BaseResource;

import ch.boye.httpclientandroidlib.HttpResponse;

/**
 * Talk to an fxa-oauth-server to get "implicitly granted" OAuth tokens.
 * <p>
 * To use this client, you will need a pre-allocated fxa-oauth-server
 * "client_id" with special "implicit grant" permissions.
 * <p>
 * This client was written against the API documented at <a href="https://github.com/mozilla/fxa-oauth-server/blob/41538990df9e91158558ae5a8115194383ac3b05/docs/api.md">https://github.com/mozilla/fxa-oauth-server/blob/41538990df9e91158558ae5a8115194383ac3b05/docs/api.md</a>.
 */
public class FxAccountOAuthClient10 extends FxAccountAbstractClient {
  protected static final String LOG_TAG = FxAccountOAuthClient10.class.getSimpleName();

  protected static final String AUTHORIZATION_RESPONSE_TYPE = "token";

  protected static final String JSON_KEY_ACCESS_TOKEN = "access_token";
  protected static final String JSON_KEY_ASSERTION = "assertion";
  protected static final String JSON_KEY_CLIENT_ID = "client_id";
  protected static final String JSON_KEY_RESPONSE_TYPE = "response_type";
  protected static final String JSON_KEY_SCOPE = "scope";
  protected static final String JSON_KEY_STATE = "state";
  protected static final String JSON_KEY_TOKEN_TYPE = "token_type";

  // access_token: A string that can be used for authorized requests to service providers.
  // scope: A string of space-separated permissions that this token has. May differ from requested scopes, since user can deny permissions.
  // token_type: A string representing the token type. Currently will always be "bearer".
  protected static final String[] AUTHORIZATION_RESPONSE_REQUIRED_STRING_FIELDS = new String[] { JSON_KEY_ACCESS_TOKEN, JSON_KEY_SCOPE, JSON_KEY_TOKEN_TYPE };

  public FxAccountOAuthClient10(String serverURI, Executor executor) {
    super(serverURI, executor);
  }

//  /**
//   * The server's URI.
//   * <p>
//   * We assume throughout that this ends with a trailing slash (and guarantee as
//   * much in the constructor).
//   */
//  protected final String serverURI;
//
//  protected final Executor executor;
//
//  public FxAccountOAuthClient10(String serverURI, Executor executor) {
//    if (serverURI == null) {
//      throw new IllegalArgumentException("Must provide a server URI.");
//    }
//    if (executor == null) {
//      throw new IllegalArgumentException("Must provide a non-null executor.");
//    }
//    this.serverURI = serverURI.endsWith("/") ? serverURI : serverURI + "/";
//    if (!this.serverURI.endsWith("/")) {
//      throw new IllegalArgumentException("Constructed serverURI must end with a trailing slash: " + this.serverURI);
//    }
//    this.executor = executor;
//  }
//
//  /**
//   * Process a typed value extracted from a successful response (in an
//   * endpoint-dependent way).
//   */
//  public interface RequestDelegate<T> {
//    public void handleError(Exception e);
//    public void handleFailure(FxAccountAbstractClientRemoteException e);
//    public void handleSuccess(T result);
//  }
//
//  /**
//   * Intepret a response from the auth server.
//   * <p>
//   * Throw an appropriate exception on errors; otherwise, return the response's
//   * status code.
//   *
//   * @return response's HTTP status code.
//   * @throws FxAccountClientException
//   */
//  public static int validateResponse(HttpResponse response) throws FxAccountAbstractClientRemoteException {
//    final int status = response.getStatusLine().getStatusCode();
//    if (status == 200) {
//      return status;
//    }
//    int code;
//    int errno;
//    String error;
//    String message;
//    ExtendedJSONObject body;
//    try {
//      body = new SyncStorageResponse(response).jsonObjectBody();
//      body.throwIfFieldsMissingOrMisTyped(requiredErrorStringFields, String.class);
//      body.throwIfFieldsMissingOrMisTyped(requiredErrorLongFields, Long.class);
//      code = body.getLong(JSON_KEY_CODE).intValue();
//      errno = body.getLong(JSON_KEY_ERRNO).intValue();
//      error = body.getString(JSON_KEY_ERROR);
//      message = body.getString(JSON_KEY_MESSAGE);
//    } catch (Exception e) {
//      throw new FxAccountAbstractClientMalformedResponseException(response);
//    }
//    throw new FxAccountAbstractClientRemoteException(response, code, errno, error, message, body);
//  }
//
//  protected <T> void invokeHandleError(final RequestDelegate<T> delegate, final Exception e) {
//    executor.execute(new Runnable() {
//      @Override
//      public void run() {
//        delegate.handleError(e);
//      }
//    });
//  }
//
//  protected <T> void post(BaseResource resource, final JSONObject requestBody, final RequestDelegate<T> delegate) {
//    try {
//      if (requestBody == null) {
//        resource.post((HttpEntity) null);
//      } else {
//        resource.post(requestBody);
//      }
//    } catch (UnsupportedEncodingException e) {
//      invokeHandleError(delegate, e);
//      return;
//    }
//  }
//
//  /**
//   * Translate resource callbacks into request callbacks invoked on the provided
//   * executor.
//   * <p>
//   * Override <code>handleSuccess</code> to parse the body of the resource
//   * request and call the request callback. <code>handleSuccess</code> is
//   * invoked via the executor, so you don't need to delegate further.
//   */
//  protected abstract class ResourceDelegate<T> extends BaseResourceDelegate {
//    protected abstract void handleSuccess(final int status, HttpResponse response, final ExtendedJSONObject body);
//
//    protected final RequestDelegate<T> delegate;
//
//    /**
//     * Create a delegate for an un-authenticated resource.
//     */
//    public ResourceDelegate(final Resource resource, final RequestDelegate<T> delegate) {
//      super(resource);
//      this.delegate = delegate;
//    }
//
//    @Override
//    public AuthHeaderProvider getAuthHeaderProvider() {
//      return super.getAuthHeaderProvider();
//    }
//
//    @Override
//    public String getUserAgent() {
//      return FxAccountConstants.USER_AGENT;
//    }
//
//    @Override
//    public void handleHttpResponse(HttpResponse response) {
//      try {
//        final int status = validateResponse(response);
//        invokeHandleSuccess(status, response);
//      } catch (FxAccountAbstractClientRemoteException e) {
//        invokeHandleFailure(e);
//      }
//    }
//
//    protected void invokeHandleFailure(final FxAccountAbstractClientRemoteException e) {
//      executor.execute(new Runnable() {
//        @Override
//        public void run() {
//          delegate.handleFailure(e);
//        }
//      });
//    }
//
//    protected void invokeHandleSuccess(final int status, final HttpResponse response) {
//      executor.execute(new Runnable() {
//        @Override
//        public void run() {
//          try {
//            ExtendedJSONObject body = new SyncResponse(response).jsonObjectBody();
//            ResourceDelegate.this.handleSuccess(status, response, body);
//          } catch (Exception e) {
//            delegate.handleError(e);
//          }
//        }
//      });
//    }
//
//    @Override
//    public void handleHttpProtocolException(final ClientProtocolException e) {
//      invokeHandleError(delegate, e);
//    }
//
//    @Override
//    public void handleHttpIOException(IOException e) {
//      invokeHandleError(delegate, e);
//    }
//
//    @Override
//    public void handleTransportException(GeneralSecurityException e) {
//      invokeHandleError(delegate, e);
//    }
//
//    @Override
//    public void addHeaders(HttpRequestBase request, DefaultHttpClient client) {
//      super.addHeaders(request, client);
//
//      // The basics.
//      final Locale locale = Locale.getDefault();
//      request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, Utils.getLanguageTag(locale));
//      request.addHeader(HttpHeaders.ACCEPT, ACCEPT_HEADER);
//    }
//  }

  /**
   * Thin container for an authorization response.
   */
  public static class AuthorizationResponse {
    public final String access_token;
    public final String token_type;
    public final String scope;

    public AuthorizationResponse(String access_token, String token_type, String scope) {
      this.access_token = access_token;
      this.token_type = token_type;
      this.scope = scope;
    }
  }

  public void authorization(String client_id, String assertion, String state, String scope,
                            RequestDelegate<AuthorizationResponse> delegate) {
    BaseResource resource;
    try {
      resource = new BaseResource(new URI(serverURI + "authorization"));
    } catch (URISyntaxException e) {
      invokeHandleError(delegate, e);
      return;
    }

    resource.delegate = new ResourceDelegate<AuthorizationResponse>(resource, delegate) {
      @Override
      public void handleSuccess(int status, HttpResponse response, ExtendedJSONObject body) {
        try {
          body.throwIfFieldsMissingOrMisTyped(AUTHORIZATION_RESPONSE_REQUIRED_STRING_FIELDS, String.class);
          String access_token = body.getString(JSON_KEY_ACCESS_TOKEN);
          String token_type = body.getString(JSON_KEY_TOKEN_TYPE);
          String scope = body.getString(JSON_KEY_SCOPE);
          delegate.handleSuccess(new AuthorizationResponse(access_token, token_type, scope));
          return;
        } catch (Exception e) {
          delegate.handleError(e);
          return;
        }
      }
    };

    final ExtendedJSONObject requestBody = new ExtendedJSONObject();
    requestBody.put(JSON_KEY_RESPONSE_TYPE, AUTHORIZATION_RESPONSE_TYPE);
    requestBody.put(JSON_KEY_CLIENT_ID, client_id);
    requestBody.put(JSON_KEY_ASSERTION, assertion);
    if (scope != null) {
      requestBody.put(JSON_KEY_SCOPE, scope);
    }
    if (state != null) {
      requestBody.put(JSON_KEY_STATE, state);
    }

    post(resource, requestBody, delegate);
  }
}
