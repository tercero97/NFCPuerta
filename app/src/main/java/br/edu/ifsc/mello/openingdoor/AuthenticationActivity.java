package br.edu.ifsc.mello.openingdoor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.daon.identityx.exception.UafProcessingException;
import com.daon.identityx.uaf.FidoOperation;

import org.json.JSONException;
import org.json.JSONObject;


public class AuthenticationActivity extends BaseActivity {

    private View mProgressView;
    private UserAuthenticationTask mAuthTask = null;
    private UserResponseAsyncTask mUserResponseAsyncTask = null;
    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);
        mProgressView = findViewById(R.id.auth_progress);
        mSharedPreferences = ApplicationContextDoorLock.getsSharedPreferences();
        String username = mSharedPreferences.getString("usernameMain", "");
        if (!username.isEmpty()) {
            this.attemptAuth();
        } else {
            Toast.makeText(this, "You need to register an authenticator first", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void attemptAuth() {
        if (mAuthTask != null) {
            return;
        }
        showProgress(true);
        mAuthTask = new UserAuthenticationTask(this);
        String url = mSharedPreferences.getString("fido_server_endpoint", "");
        String endpoint = mSharedPreferences.getString("fido_auth_request", "");
        mAuthTask.execute(url + endpoint);
    }


    //Sending the response message created by the client to the server
    @Override
    protected void processUafClientResponse(String uafResponseJson) {
        mAuthTask = null;
        showProgress(true);
        if (mUserResponseAsyncTask != null) {
            return;
        }
        mUserResponseAsyncTask = new UserResponseAsyncTask();
        String url = mSharedPreferences.getString("fido_server_endpoint", "");
        String endpoint = mSharedPreferences.getString("fido_auth_response", "");
        mUserResponseAsyncTask.execute(uafResponseJson, url + endpoint);
    }

    public void finishProcessing(String result) {
        if (result != null) {
            Bundle extras = new Bundle();
            extras.putString("result", result);
            Intent intent = new Intent();
            intent.putExtras(extras);
            setResult(RESULT_OK, intent);
        } else {
            Toast.makeText(getApplicationContext(), "Something is wrong!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onActivityResultFailure(String errorMsg) {
        mAuthTask = null;
        mUserResponseAsyncTask = null;
        showProgress(false);
        Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Represents an asynchronous  task used to authenticate the user.
     */
    public class UserAuthenticationTask extends AsyncTask<String, Integer, String> {

        private String result;
        private boolean done;
        private AuthenticationActivity mAuthenticationActivity;

        public UserAuthenticationTask(AuthenticationActivity authenticationActivity) {
            mAuthenticationActivity = authenticationActivity;
            result = null;
            done = false;
        }

        public boolean isDone() {
            return done;
        }

        public String getResult() {
            return result;
        }

        @Override
        protected String doInBackground(String... args) {
            done = true;
            try {
                result = HttpUtils.get(args[0]).getPayload();
            } catch (Exception e) {
                return "";
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != "") {
                mAuthTask = null;
                this.result = result;
                this.done = true;
                showProgress(false);
                setCurrentFidoOperation(FidoOperation.Authentication);
                Intent intent = getUafClientUtils()
                        .getUafOperationIntent(FidoOperation.Authentication, result);
                Bundle extra = intent.getExtras();
                intent.putExtras(extra);
                try {
                    sendUafClientIntent(intent, FidoOpCommsType.Return);
                } catch (UafProcessingException e) {
                    Toast.makeText(mAuthenticationActivity.getApplicationContext(), R.string.no_fido_client_found, Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(mAuthenticationActivity.getApplicationContext(), R.string.connection_error, Toast.LENGTH_LONG).show();
                finish();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    public class UserResponseAsyncTask extends AsyncTask<String, Void, String> {


        @Override
        protected String doInBackground(String... params) {
            StringBuffer res = new StringBuffer();
            String decoded = "";
            try {
                JSONObject json = new JSONObject(params[0]);
                decoded = json.getString("uafProtocolMessage").replace("\\", "");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            res.append("#uafMessageegOut\n" + decoded);
            res.append("\n\n#ServerResponse\n");
            String serverResponse = HttpUtils.post(params[1], decoded).getPayload();
            res.append(serverResponse);
            return res.toString();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            showProgress(false);
            mUserResponseAsyncTask = null;
            finishProcessing(result);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            showProgress(false);
            mUserResponseAsyncTask = null;
        }
    }


}
