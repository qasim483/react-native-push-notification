package com.dieam.reactnativepushnotification.modules;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationListenerService extends FirebaseMessagingService {

    private RNReceivedMessageHandler mMessageReceivedHandler;
    private FirebaseMessagingService mFirebaseServiceDelegate;

    public RNPushNotificationListenerService() {
        super();
        this.mMessageReceivedHandler = new RNReceivedMessageHandler(this);
    }

    public RNPushNotificationListenerService(FirebaseMessagingService delegate) {
        super();
        this.mFirebaseServiceDelegate = delegate;
        this.mMessageReceivedHandler = new RNReceivedMessageHandler(delegate);
    }

    @Override
    public void onNewToken(String token) {
        final String deviceToken = token;
        final FirebaseMessagingService serviceRef = (this.mFirebaseServiceDelegate == null) ? this : this.mFirebaseServiceDelegate;
        Log.d(LOG_TAG, "Refreshed token: " + deviceToken);

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                // Construct and load our normal React JS code bundle
                final ReactInstanceManager mReactInstanceManager = ((ReactApplication)serviceRef.getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();
                // If it's constructed, send a notification
                if (context != null) {
                    handleNewToken((ReactApplicationContext) context, deviceToken);
                } else {
                    // Otherwise wait for construction, then send the notification
                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            handleNewToken((ReactApplicationContext) context, deviceToken);
                            mReactInstanceManager.removeReactInstanceEventListener(this);
                        }
                    });
                    if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                        // Construct it in the background
                        mReactInstanceManager.createReactContextInBackground();
                    }
                }
            }
        });
    }

    private void handleNewToken(ReactApplicationContext context, String token) {
        RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery(context);

        WritableMap params = Arguments.createMap();
        params.putString("deviceToken", token);
        jsDelivery.sendEvent("remoteNotificationsRegistered", params);
    }

    @Override
    public void onMessageReceived(RemoteMessage message)
    {
        mMessageReceivedHandler.handleReceivedMessage(message);
    
        // CHECK IF MESSAGE CONTAINS A DATA PAYLOAD.
        if (message.getData().size() > 0) {
            try
            {
                Log.d ("OnMessageFCM",message.getData ()+"");
                JSONObject fcmData = new JSONObject (message.getData ());
                if (fcmData.getString ("LKCallType").equals ("call") && fcmData.getBoolean ("updateFCMPR"))
                {
                    this.updateCallRequest (Integer.parseInt(fcmData.getString ("call_id")));
                }
            } catch (JSONException e)
            {
                e.printStackTrace ();
            }
        }
    }
    
    // FUNCTION TO UPDATE FLAG IN DB TO INDICATE THAT THE DEVICE HAS RECIEVED INCOMING CALL.
    public void updateCallRequest (int callId)
    {
        Log.d ("OnMessageFCM","in func");
        // REQUEST TO SEND ON THE URL.
        JSONObject params = new JSONObject();
        try
        {
            // GET THE DND STATUS.
            Context context = getApplicationContext ();
            NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            int dndStatus = notificationManager.getCurrentInterruptionFilter ();
    
            // USE AUDIO MANAGER TO GET THE RINGTONE AND MEDIA VOLUME.
            AudioManager am = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);
            int volumeLevelRing = am.getStreamVolume(AudioManager.STREAM_RING);
            int volumeLevelMusic = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            
            // CHECK IF THE DEVICE IS LOCKED OR NOT?
            KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            Boolean isPhoneLocked = myKM.inKeyguardRestrictedInputMode();
            PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            Boolean isScreenAwake = powerManager.isInteractive();
            
            Log.d ("isDNDon", dndStatus + "");
            Log.d ("OnMessageFCM","in try");
            JSONObject callData = new JSONObject ();
            JSONObject deviceData = new JSONObject ();
    
            // CALL BATTERY MANAGER SERVICE TO GET THE DEVICE BATTERY.
            BatteryManager batteryManager = (BatteryManager) context.getSystemService (context.BATTERY_SERVICE);
    
            // GET THE BATTERY PERCENTAGE AND STORE IT IN A INT VARIABLE
            int batteryLevel = batteryManager.getIntProperty (BatteryManager.BATTERY_PROPERTY_CAPACITY);
            
            callData.put ("wasFCMPayloadReceived", "Y");
            callData.put("status_id", "FCMPR");
            deviceData.put("dnd_status", dndStatus);
            deviceData.put("volume_level_ring", volumeLevelRing);
            deviceData.put("volume_level_media", volumeLevelMusic);
            deviceData.put("battery_level", batteryLevel);
            deviceData.put("is_device_locked", isPhoneLocked);
            deviceData.put("is_screen_awake", isScreenAwake);
            callData.put ("device_data",deviceData);
            params.put("call_data", callData);
            params.put("call_id", callId);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        String url = "https://letskinect.com/lkpserver/api/call/log/update";
        Log.d ("updateDidMobileRing", url);
        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
        ( Request.Method.POST, url, params, new Response.Listener<JSONObject>()
        {
            @Override
            public void onResponse (JSONObject response)
            {
                Log.d("wasFCMPayloadReceived",response.toString ());
            }
        },new Response.ErrorListener()
        {
            @Override
            public void onErrorResponse(VolleyError error)
            {
                Log.d("wasFCMPayloadReceived","Something went wrong. "+ error);
            }
        });
        //VolleyController.getInstance(CallNotificationService.this).addToRequestQueue(jsonObjectRequest);
        RequestQueue queue = Volley.newRequestQueue( getApplicationContext() );
        queue.add(jsonObjectRequest);
    }
}
