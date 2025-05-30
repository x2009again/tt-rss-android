package org.fox.ttrss;

import static org.fox.ttrss.ApiCommon.ApiError;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import com.google.gson.JsonElement;

import java.util.HashMap;

public class ApiRequest extends AsyncTask<HashMap<String, String>, Integer, JsonElement> implements ApiCommon.ApiCaller {

    protected int m_apiStatusCode = 0;

    private final Context m_context;
    protected String m_lastErrorMessage;

    protected ApiError m_lastError;

    public ApiRequest(Context context) {
        m_context = context;
        m_lastError = ApiError.UNKNOWN_ERROR;
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("unchecked")
    public void execute(HashMap<String, String> map) {
        super.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, map);
    }

    public int getErrorMessage() {
        return ApiCommon.getErrorMessage(m_lastError);
    }

    @Override
    protected JsonElement doInBackground(HashMap<String, String>... params) {
        return ApiCommon.performRequest(m_context, params[0], this);
    }

    @Override
    public void setStatusCode(int statusCode) {
        m_apiStatusCode = statusCode;
    }

    @Override
    public void setLastError(ApiError lastError) {
        m_lastError = lastError;
    }

    @Override
    public void setLastErrorMessage(String message) {
        m_lastErrorMessage = message;
    }

    @Override
    public void notifyProgress(int progress) {
        publishProgress(progress);
    }
}
