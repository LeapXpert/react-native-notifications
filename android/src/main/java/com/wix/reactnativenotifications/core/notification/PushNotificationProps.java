package com.wix.reactnativenotifications.core.notification;

import android.os.Bundle;
import java.util.ArrayList;

public class PushNotificationProps {

    protected Bundle mBundle;

    public PushNotificationProps() {
        mBundle = new Bundle();
    }

    public PushNotificationProps(String title, String body) {
        mBundle = new Bundle();
        mBundle.putString("title", title);
        mBundle.putString("body", body);
    }

    public PushNotificationProps(Bundle bundle) {
        mBundle = bundle;
    }

    public String getTitle() {
        return mBundle.getString("title");
    }

    public String getBody() {
        return mBundle.getString("body");
    }

    public Boolean getIsOngoing() { return mBundle.getBoolean("isOngoing"); }

    public ArrayList<String> getActions() { return mBundle.getStringArrayList("actions"); }

    public void setAction(String action) { mBundle.putString("action", action); }

    public Bundle asBundle() {
        return (Bundle) mBundle.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (String key : mBundle.keySet()) {
            sb.append(key).append("=").append(mBundle.get(key)).append(", ");
        }
        return sb.toString();
    }

    protected PushNotificationProps copy() {
        return new PushNotificationProps((Bundle) mBundle.clone());
    }
}
