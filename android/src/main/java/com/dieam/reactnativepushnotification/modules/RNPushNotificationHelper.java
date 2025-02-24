// @custom
package com.dieam.reactnativepushnotification.modules;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.dieam.reactnativepushnotification.R;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.core.text.HtmlCompat;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.KEY_TEXT_REPLY;
import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;
import static com.dieam.reactnativepushnotification.modules.RNPushNotificationAttributes.fromJson;

public class RNPushNotificationHelper {
    public static final String PREFERENCES_KEY = "rn_push_notification";
    private static final long DEFAULT_VIBRATION = 300L;
    
    private Context context;
    private RNPushNotificationConfig config;
    private final SharedPreferences scheduledNotificationsPersistence;
    
    public RNPushNotificationHelper(Application context) {
        this.context = context;
        this.config = new RNPushNotificationConfig(context);
        this.scheduledNotificationsPersistence = context.getSharedPreferences(RNPushNotificationHelper.PREFERENCES_KEY, Context.MODE_PRIVATE);
    }
    
    public Class getMainActivityClass() {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private AlarmManager getAlarmManager() {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
    
    public void invokeApp(Bundle bundle) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        
        try {
            Class<?> activityClass = Class.forName(className);
            Intent activityIntent = new Intent(context, activityClass);
            
            if(bundle != null) {
                activityIntent.putExtra("notification", bundle);
            }
            
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(activityIntent);
        } catch(Exception e) {
            Log.e(LOG_TAG, "Class not found", e);
            return;
        }
    }
    
    private PendingIntent toScheduleNotificationIntent(Bundle bundle) {
        try {
            int notificationID = Integer.parseInt (bundle.getString ("id"));
            
            Intent notificationIntent = new Intent(context, RNPushNotificationPublisher.class);
            notificationIntent.putExtra(RNPushNotificationPublisher.NOTIFICATION_ID, notificationID);
            notificationIntent.putExtras(bundle);
            
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            
            return PendingIntent.getBroadcast(context, notificationID, notificationIntent, flags);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to parse Notification ID", e);
        }
        
        return null;
    }
    
    public void sendNotificationScheduled(Bundle bundle) {
        Class intentClass = getMainActivityClass();
        if (intentClass == null) {
            Log.e(LOG_TAG, "No activity class found for the scheduled notification");
            return;
        }
        
        if (bundle.getString("message") == null) {
            Log.e(LOG_TAG, "No message specified for the scheduled notification");
            return;
        }
        
        if (bundle.getString("id") == null) {
            Log.e(LOG_TAG, "No notification ID specified for the scheduled notification");
            return;
        }
        
        double fireDate = bundle.getDouble("fireDate");
        if (fireDate == 0) {
            Log.e(LOG_TAG, "No date specified for the scheduled notification");
            return;
        }
        
        RNPushNotificationAttributes notificationAttributes = new RNPushNotificationAttributes(bundle);
        String id = notificationAttributes.getId();
        
        Log.d(LOG_TAG, "Storing push notification with id " + id);
        
        SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
        editor.putString(id, notificationAttributes.toJson().toString());
        editor.apply();
        
        boolean isSaved = scheduledNotificationsPersistence.contains(id);
        if (!isSaved) {
            Log.e(LOG_TAG, "Failed to save " + id);
        }
        
        sendNotificationScheduledCore(bundle);
    }
    
    public void sendNotificationScheduledCore(Bundle bundle) {
        long fireDate = (long) bundle.getDouble("fireDate");
        boolean allowWhileIdle = bundle.getBoolean("allowWhileIdle");
        
        // If the fireDate is in past, this will fire immediately and show the
        // notification to the user
        PendingIntent pendingIntent = toScheduleNotificationIntent(bundle);
        
        if (pendingIntent == null) {
            return;
        }
        
        Log.d(LOG_TAG, String.format("Setting a notification with id %s at time %s",
            bundle.getString("id"), Long.toString(fireDate)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (allowWhileIdle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getAlarmManager().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
            } else {
                getAlarmManager().setExact(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
            }
        } else {
            getAlarmManager().set(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        }
    }
    
    
    public void sendToNotificationCentre(final Bundle bundle) {
        RNPushNotificationPicturesAggregator aggregator = new RNPushNotificationPicturesAggregator(new RNPushNotificationPicturesAggregator.Callback() {
            public void call(Bitmap largeIconImage, Bitmap bigPictureImage, Bitmap bigLargeIconImage) {
                sendToNotificationCentreWithPicture(bundle, largeIconImage, bigPictureImage, bigLargeIconImage);
            }
        });
        
        aggregator.setLargeIconUrl(context, bundle.getString("largeIconUrl"));
        aggregator.setBigLargeIconUrl(context, bundle.getString("bigLargeIconUrl"));
        aggregator.setBigPictureUrl(context, bundle.getString("bigPictureUrl"));
    }
    
    public void sendToNotificationCentreWithPicture(Bundle bundle, Bitmap largeIconBitmap, Bitmap bigPictureBitmap, Bitmap bigLargeIconBitmap) {
        try {
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                Log.e(LOG_TAG, "No activity class found for the notification");
                return;
            }
            
            if (bundle.getString("message") == null) {
                // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
                Log.d(LOG_TAG, "Ignore this message if you sent data-only notification. Cannot send to notification centre because there is no 'message' field in: " + bundle);
                return;
            }
            
            String notificationIdString = bundle.getString("id");
            if (notificationIdString == null) {
                Log.e(LOG_TAG, "No notification ID specified for the notification");
                return;
            }
            
            Resources res = context.getResources();
            String packageName = context.getPackageName();
            
            String title = bundle.getString("title");
            if (title == null) {
                ApplicationInfo appInfo = context.getApplicationInfo();
                title = context.getPackageManager().getApplicationLabel(appInfo).toString();
            }
            
            int priority = NotificationCompat.PRIORITY_HIGH;
            final String priorityString = bundle.getString("priority");
            
            if (priorityString != null) {
                switch (priorityString.toLowerCase()) {
                    case "max":
                        priority = NotificationCompat.PRIORITY_MAX;
                        break;
                    case "high":
                        priority = NotificationCompat.PRIORITY_HIGH;
                        break;
                    case "low":
                        priority = NotificationCompat.PRIORITY_LOW;
                        break;
                    case "min":
                        priority = NotificationCompat.PRIORITY_MIN;
                        break;
                    case "default":
                        priority = NotificationCompat.PRIORITY_DEFAULT;
                        break;
                    default:
                        priority = NotificationCompat.PRIORITY_HIGH;
                }
            }
            
            int visibility = NotificationCompat.VISIBILITY_PRIVATE;
            final String visibilityString = bundle.getString("visibility");
            
            if (visibilityString != null) {
                switch (visibilityString.toLowerCase()) {
                    case "private":
                        visibility = NotificationCompat.VISIBILITY_PRIVATE;
                        break;
                    case "public":
                        visibility = NotificationCompat.VISIBILITY_PUBLIC;
                        break;
                    case "secret":
                        visibility = NotificationCompat.VISIBILITY_SECRET;
                        break;
                    default:
                        visibility = NotificationCompat.VISIBILITY_PRIVATE;
                }
            }
            
            String channel_id = bundle.getString("channelId");
            
            if(channel_id == null) {
                channel_id = this.config.getNotificationDefaultChannelId();
            }
            
            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channel_id)
                                                          .setContentTitle(title)
                                                          .setTicker(bundle.getString("ticker"))
                                                          .setVisibility(visibility)
                                                          .setPriority(priority)
                                                          .setAutoCancel(bundle.getBoolean("autoCancel", true))
                                                          .setOnlyAlertOnce(bundle.getBoolean("onlyAlertOnce", false));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24 and higher
                // Restore showing timestamp on Android 7+
                // Source: https://developer.android.com/reference/android/app/Notification.Builder.html#setShowWhen(boolean)
                boolean showWhen = bundle.getBoolean("showWhen", true);
                
                notification.setShowWhen(showWhen);
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26 and higher
                // Changing Default mode of notification
                notification.setDefaults(Notification.DEFAULT_LIGHTS);
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) { // API 20 and higher
                String group = bundle.getString("group");
                
                if (group != null) {
                    notification.setGroup(group);
                }
                
                if (bundle.containsKey("groupSummary") || bundle.getBoolean("groupSummary")) {
                    notification.setGroupSummary(bundle.getBoolean("groupSummary"));
                }
            }
            
            String numberString = bundle.getString("number");
            
            if (numberString != null) {
                notification.setNumber(Integer.parseInt(numberString));
            }
            
            // Small icon
            int smallIconResId = 0;
            
            String smallIcon = bundle.getString("smallIcon");
            
            if (smallIcon != null && !smallIcon.isEmpty()) {
                smallIconResId = res.getIdentifier(smallIcon, "drawable", packageName);
                if (smallIconResId == 0) {
                    smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
                }
            } else if(smallIcon == null) {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }
            
            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
                
                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }
            
            notification.setSmallIcon(smallIconResId);
            
            // Large icon
            if(largeIconBitmap == null) {
                int largeIconResId = 0;
                
                String largeIcon = bundle.getString("largeIcon");
                
                if (largeIcon != null && !largeIcon.isEmpty()) {
                    largeIconResId = res.getIdentifier(largeIcon, "drawable", packageName);
                    if (largeIconResId == 0) {
                        largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
                    }
                } else if(largeIcon == null) {
                    largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
                }
                
                // Before Lolipop there was no large icon for notifications.
                if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                    largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);
                }
            }
            
            if (largeIconBitmap != null){
                notification.setLargeIcon(largeIconBitmap);
            }
            
            String message = bundle.getString("message");
            
            notification.setContentText(message);
            
            String subText = bundle.getString("subText");
            
            if (subText != null) {
                notification.setSubText(subText);
            }
            
            String bigText = bundle.getString("bigText");
            
            if (bigText == null) {
                bigText = message;
            }
            
            NotificationCompat.Style style;
            
            if(bigPictureBitmap != null) {
                
                // Big large icon
                if(bigLargeIconBitmap == null) {
                    int bigLargeIconResId = 0;
                    
                    String bigLargeIcon = bundle.getString("bigLargeIcon");
                    
                    if (bigLargeIcon != null && !bigLargeIcon.isEmpty()) {
                        bigLargeIconResId = res.getIdentifier(bigLargeIcon, "mipmap", packageName);
                        if (bigLargeIconResId != 0) {
                            bigLargeIconBitmap = BitmapFactory.decodeResource(res, bigLargeIconResId);
                        }
                    }
                }
                
                style = new NotificationCompat.BigPictureStyle()
                            .bigPicture(bigPictureBitmap)
                            .setBigContentTitle(title)
                            .setSummaryText(message)
                            .bigLargeIcon(bigLargeIconBitmap);
            }
            else {
                bigText = bundle.getString("bigText");
                
                if (bigText == null) {
                    style = new NotificationCompat.BigTextStyle().bigText(message);
                } else {
                    Spanned styledText = HtmlCompat.fromHtml(bigText, HtmlCompat.FROM_HTML_MODE_LEGACY);
                    style = new NotificationCompat.BigTextStyle().bigText(styledText);
                }
            }
            
            if (!bundle.containsKey ("customLayout") || android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                notification.setStyle (style);
            }
            
            Intent intent = new Intent(context, intentClass);
            Intent fullIntent =  new Intent (context, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            bundle.putBoolean("foreground", this.isApplicationInForeground());
            bundle.putBoolean("userInteraction", true);
            Bundle contentBundle = (Bundle) bundle.clone ();
            contentBundle.putBoolean ("contentIntent", true);
            intent.putExtra ("notification", contentBundle);
            
            // Add message_id to intent so react-native-firebase/messaging can identify it
            String messageId = bundle.getString("messageId");
            if (messageId != null) {
                intent.putExtra("message_id", messageId);
            }
            
            Uri soundUri = null;
            if (!bundle.containsKey("playSound") || bundle.getBoolean("playSound")) {
                String soundName = bundle.getString("soundName");
                soundUri = getSoundUri(soundName);
                notification.setSound(soundUri);
            }
            
            if (soundUri == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification.setSound(null);
            }
            
            if (bundle.containsKey("ongoing") || bundle.getBoolean("ongoing")) {
                notification.setOngoing(bundle.getBoolean("ongoing"));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                notification.setCategory(NotificationCompat.CATEGORY_CALL);
//                if (bundle.containsKey ("color")) {
//                    Integer color = Integer.valueOf (bundle.getString ("color"));
//                    Log.d ("color_notification", "" + color);
//                    Boolean colorized = bundle.getBoolean ("colorized");
//                    notification.setColor (color);
//                    if (colorized != null) {
//                        notification.setColorized (colorized);
//                    }
//                }
//                else
//                {
//                    int defaultColor = this.config.getNotificationColor ();
//                    notification.setColor(defaultColor);
//                }
                int defaultColor = this.config.getNotificationColor ();
                notification.setColor(defaultColor);
            }
            
            int notificationID = Integer.parseInt(notificationIdString);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationID, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            
            NotificationManager notificationManager = notificationManager();
            long[] vibratePattern = new long[]{0};
            if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate"))
            {
                long vibration = bundle.containsKey ("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
                if (vibration == 0)
                    vibration = DEFAULT_VIBRATION;
                vibratePattern = new long[]{0, vibration};
                notification.setVibrate (vibratePattern);
            }
            Long timeoutAfter = (long) bundle.getDouble("timeoutAfter");
            Log.d ("TimeoutAfter", timeoutAfter+"");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Define the shortcutId
                String shortcutId = bundle.getString("shortcutId");
                
                if (shortcutId != null) {
                    notification.setShortcutId(shortcutId);
                }
                
                // IF THIS IS NOT THE FOREGROUND SERVICE THEN WE WILL CHECK FOR TIMEOUT.
                // BECAUSE FOREGROUND SERVICES CAN NOT BE DISMISSED FROM HERE.
                if (!bundle.containsKey ("asForegroundService") || !bundle.getBoolean ("asForegroundService")) {
                    
                    if (timeoutAfter != null && timeoutAfter >= 0) {
                        notification.setTimeoutAfter(timeoutAfter);
                    }
                }
            }
            
            Long when = (long) bundle.getDouble("when");
            
            if (when != null && when >= 0) {
                notification.setWhen(when);
            }
            
            notification.setUsesChronometer(bundle.getBoolean("usesChronometer", false));
            
            notification.setChannelId(channel_id);
            notification.setContentIntent(pendingIntent);
            Log.d ("debugNotification", "step2");
            
            // IF FULL SCREEN INTENT IS ENABLED THEN DISPLAY IT.
            if (bundle.containsKey ("enableFullScreenIntent") && bundle.getBoolean ("enableFullScreenIntent"))
            {
                Bundle fullIntentBundle = (Bundle) bundle.clone ();
                fullIntentBundle.putBoolean ("fullScreenIntent", true);
                fullIntent.putExtra ("notification", fullIntentBundle);
                PendingIntent fullScreenIntent = PendingIntent.getActivity ( context, 121, fullIntent,PendingIntent.FLAG_UPDATE_CURRENT );
                notification.setFullScreenIntent (fullScreenIntent, true);
            }
            if (bundle.containsKey ("showBubble") && bundle.getBoolean ("showBubble"))
            {
//                Log.d ("show_bubble","yes");
//                Person person = new Person.Builder ()
//                                    .setBot(true)
//                                    .setName("A Bubble Bot")
//                                    .setImportant(true)
//                                    .build();
//                notification.setBubbleMetadata(
//                    new NotificationCompat.BubbleMetadata.Builder()
//                        .setDesiredHeight(600)
//                        .setIntent(pendingIntent)
//                        .setAutoExpandBubble(true)
//                        .setSuppressNotification(true)
//                        .setIcon(IconCompat.createWithResource(context, R.drawable.icon))
//                        .build()
//                ).addPerson(String.valueOf (person));
            }
            
            Log.d ("debugNotification", "step3");
            
            // IF THERE IS A INSTRUCTION TO RUN THIS AS FOREGROUND SERVICE THEN RUN IT AS FOREGROUND.
            if ((!bundle.containsKey ("enableFullScreenIntent") || !bundle.getBoolean ("enableFullScreenIntent")) && (bundle.containsKey ("asForegroundService") && bundle.getBoolean ("asForegroundService")))
            {
                PendingIntent fullScreenIntent = PendingIntent.getActivity(context, 123, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
                notification.setFullScreenIntent (fullScreenIntent, true);
            }
            RemoteViews notificationLayout = null;
            
            if (bundle.containsKey ("customLayout") && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.N)
            {
                notificationLayout = new RemoteViews (context.getPackageName (), com.dieam.reactnativepushnotification.R.layout.notification);
                if (bundle.containsKey ("color"))
                {
                    int color = 0xFF34A0CE; // NORMAL COLOR, BLUE.
                    int colorButtonBar = 0xFF43688A;
                    String colorDec = bundle.getString ("color");
                    if (colorDec.equals ("14128198"))
                    {
                        color = 0xFFD79446; // MEDIUM COLOR, ORANGE.
                        colorButtonBar = 0xFFAC7028;
                    }
                    if (colorDec.equals ("13329508"))
                    {
                        color = 0xFFCB6464; // URGENT COLOR. RED.
                        colorButtonBar = 0xFF8D2D2D;
                    }
                    Log.d ("color_notification", "" + color);
                    notificationLayout.setInt (R.id.layout, "setBackgroundColor", color);
                    notificationLayout.setInt (R.id.lkButtonsBar, "setBackgroundColor", colorButtonBar);
                }
                notificationLayout.setTextViewText (R.id.lkTitle, " | "+title);
                notificationLayout.setTextViewText (R.id.lkText, message);
                if (largeIconBitmap != null)
                {
                    notificationLayout.setImageViewBitmap (R.id.lkUserAvatar,largeIconBitmap);
                }
            }
            
            JSONArray actionsArray = null;
            try {
                actionsArray = bundle.getString("actions") != null ? new JSONArray(bundle.getString("actions")) : null;
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Exception while converting actions to JSON object.", e);
            }
            
            if (actionsArray != null) {
                // No icon for now. The icon value of 0 shows no icon.
                int icon = 0;
                
                // Add button for each actions.
                for (int i = 0; i < actionsArray.length(); i++) {
                    String action;
                    try {
                        action = actionsArray.getString(i);
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "Exception while getting action from actionsArray.", e);
                        continue;
                    }
                    
                    
                    Intent actionIntent = new Intent(context, RNPushNotificationActions.class);
                    actionIntent.setAction(packageName + ".ACTION_" + i);
                    
                    actionIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    
                    // Add "action" for later identifying which button gets pressed.
                    bundle.putString("action", action);
                    actionIntent.putExtra("notification", bundle);
                    actionIntent.setPackage(packageName);
                    if (messageId != null) {
                        intent.putExtra("message_id", messageId);
                    }
                    
                    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
                    
                    PendingIntent pendingActionIntent = PendingIntent.getBroadcast(context, notificationID, actionIntent, flags);
                    
                    if(action.equals("ReplyInput")){
                        //Action with inline reply
                        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH){
                            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                                                          .setLabel(bundle.getString("reply_placeholder_text"))
                                                          .build();
                            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                                icon, bundle.getString("reply_button_text"), pendingActionIntent)
                                                                        .addRemoteInput(remoteInput)
                                                                        .setAllowGeneratedReplies(true)
                                                                        .build();
                            
                            notification.addAction(replyAction);
                        }
                        else{
                            // The notification will not have action
                            break;
                        }
                    }
                    else
                    {
                        if (bundle.containsKey ("customLayout") && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.N)
                        {
                            // LKNotificationService lkNotificationService = new LKNotificationService ();
                            // lkNotificationService.setActionButton ( action, pendingActionIntent);
                            int btnId;
                            if (i==0)
                            {
                                btnId = R.id.lkButton1;
                            }
                            else if (i==1)
                            {
                                btnId = R.id.lkButton2;
                            }
                            else
                            {
                                btnId = R.id.lkButton3;
                            }
                            notificationLayout.setOnClickPendingIntent (btnId,pendingActionIntent);
                            notificationLayout.setTextViewText (btnId, action);
                            notificationLayout.setViewVisibility (btnId, View.VISIBLE);
                        }
                        else
                        {
                            // Add "action" for later identifying which button gets pressed
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                notification.addAction (new NotificationCompat.Action.Builder (icon, action, pendingActionIntent).build ());
                            } else {
                                notification.addAction (icon, action, pendingActionIntent);
                            }
                        }
                    }
                }
                
            }
            
            if (bundle.containsKey ("customLayout") && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.N)
            {
                //RemoteViews notificationLayout = new RemoteViews (context.getPackageName (), com.dieam.reactnativepushnotification.R.layout.notification);
                //notificationLayout.setOnClickPendingIntent (R.id.lkButton1,);
                notification.setCustomContentView (notificationLayout);
            }
            
            // Remove the notification from the shared preferences once it has been shown
            // to avoid showing the notification again when the phone is rebooted. If the
            // notification is not removed, then every time the phone is rebooted, we will
            // try to reschedule all the notifications stored in shared preferences and since
            // these notifications will be in the past time, they will be shown immediately
            // to the user which we shouldn't do. So, remove the notification from the shared
            // preferences once it has been shown to the user. If it is a repeating notification
            // it will be scheduled again.
            if (scheduledNotificationsPersistence.getString(notificationIdString, null) != null) {
                SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
                editor.remove(notificationIdString);
                editor.apply();
            }
            
            if (!(this.isApplicationInForeground() && bundle.getBoolean("ignoreInForeground"))) {
                Notification info = notification.build();
                info.defaults |= Notification.DEFAULT_LIGHTS;
                
                // IF THERE IS A INSTRUCTION TO RUN THIS AS FOREGROUND SERVICE THEN RUN IT AS FOREGROUND.
                if (bundle.containsKey ("asForegroundService") && bundle.getBoolean ("asForegroundService"))
                {
                    Intent serviceIntent = new Intent(context, LKNotificationService.class);
                    serviceIntent.putExtra ("notification", info);
                    serviceIntent.putExtra ("notificationPayload", bundle.getString ("notificationPayload"));
                    serviceIntent.putExtra ("notificationID", notificationID);
                    serviceIntent.putExtra ("timeoutAfter", timeoutAfter);
                    context.startService (serviceIntent);
                }
                else if (bundle.containsKey("tag")) {
                    String tag = bundle.getString ("tag");
                    notificationManager.notify (tag, notificationID, info);
                } else {
                    notificationManager.notify(notificationID, info);
                }
            }
            
            // Can't use setRepeating for recurring notifications because setRepeating
            // is inexact by default starting API 19 and the notifications are not fired
            // at the exact time. During testing, it was found that notifications could
            // late by many minutes.
            this.scheduleNextNotificationIfRepeating(bundle);
        } catch (Exception e) {
            Log.e(LOG_TAG, "failed to send push notification", e);
        }
    }
    
    private void scheduleNextNotificationIfRepeating(Bundle bundle) {
        String repeatType = bundle.getString("repeatType");
        long repeatTime = (long) bundle.getDouble("repeatTime");
        
        if (repeatType != null) {
            long fireDate = (long) bundle.getDouble("fireDate");
            
            boolean validRepeatType = Arrays.asList("time", "month", "week", "day", "hour", "minute").contains(repeatType);
            
            // Sanity checks
            if (!validRepeatType) {
                Log.w(LOG_TAG, String.format("Invalid repeatType specified as %s", repeatType));
                return;
            }
            
            if ("time".equals(repeatType) && repeatTime <= 0) {
                Log.w(LOG_TAG, "repeatType specified as time but no repeatTime " +
                                   "has been mentioned");
                return;
            }
            
            long newFireDate;
            if ("time".equals(repeatType)) {
                newFireDate = fireDate + repeatTime;
            } else {
                int repeatField = getRepeatField(repeatType);
                
                final Calendar nextEvent = Calendar.getInstance();
                nextEvent.setTimeInMillis(fireDate);
                // Limits repeat time increment to int instead of long
                int increment = repeatTime > 0 ? (int) repeatTime : 1;
                nextEvent.add(repeatField, increment);
                
                newFireDate = nextEvent.getTimeInMillis();
            }
            
            // Sanity check, should never happen
            if (newFireDate != 0) {
                Log.d(LOG_TAG, String.format("Repeating notification with id %s at time %s",
                    bundle.getString("id"), Long.toString(newFireDate)));
                bundle.putDouble("fireDate", newFireDate);
                this.sendNotificationScheduled(bundle);
            }
        }
    }
    
    private int getRepeatField(String repeatType) {
        switch (repeatType) {
            case "month":
                return Calendar.MONTH;
            case "week":
                return Calendar.WEEK_OF_YEAR;
            case "hour":
                return Calendar.HOUR;
            case "minute":
                return Calendar.MINUTE;
            case "day":
            default:
                return Calendar.DATE;
        }
    }
    
    private Uri getSoundUri(String soundName) {
        if (soundName == null || "default".equalsIgnoreCase(soundName)) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            
            // sound name can be full filename, or just the resource name.
            // So the strings 'my_sound.mp3' AND 'my_sound' are accepted
            // The reason is to make the iOS and android javascript interfaces compatible
            
            int resId;
            if (context.getResources().getIdentifier(soundName, "raw", context.getPackageName()) != 0) {
                resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
            } else {
                soundName = soundName.substring(0, soundName.lastIndexOf('.'));
                resId = context.getResources().getIdentifier(soundName, "raw", context.getPackageName());
            }
            
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
        }
    }
    
    public void clearNotifications() {
        Log.i(LOG_TAG, "Clearing alerts from the notification centre");
        
        NotificationManager notificationManager = notificationManager();
        notificationManager.cancelAll();
    }
    
    public void clearNotification(String tag, int notificationID) {
        Log.i(LOG_TAG, "Clearing notification: " + notificationID);
        
        NotificationManager notificationManager = notificationManager();
        if(tag != null)
        {
            // IF TAG IS "incall" THEN ITS AN INCOMING NOTIFICATION THAT IS RUNNING THROUGH FOREGROUND SERVER.
            // NOT WE NEED TO STOP THAT SERVER.
            this.closeForegroundNotification();
            notificationManager.cancel(tag, notificationID);
        } else {
            notificationManager.cancel(notificationID);
        }
    }
    
    private void closeForegroundNotification ()
    {
        context.stopService(new Intent(context, LKNotificationService.class));
        Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(it);
    }
    
    public void clearDeliveredNotifications(ReadableArray identifiers) {
        NotificationManager notificationManager = notificationManager();
        for (int index = 0; index < identifiers.size(); index++) {
            String id = identifiers.getString(index);
            Log.i(LOG_TAG, "Removing notification with id " + id);
            notificationManager.cancel(Integer.parseInt(id));
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.M)
    public WritableArray getDeliveredNotifications() {
        WritableArray result = Arguments.createArray();
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return result;
        }
        
        NotificationManager notificationManager = notificationManager();
        StatusBarNotification delivered[] = notificationManager.getActiveNotifications();
        Log.i(LOG_TAG, "Found " + delivered.length + " delivered notifications");
        /*
         * stay consistent to the return structure in
         * https://facebook.github.io/react-native/docs/pushnotificationios.html#getdeliverednotifications
         * but there is no such thing as a 'userInfo'
         */
        for (StatusBarNotification notification : delivered) {
            Notification original = notification.getNotification();
            Bundle extras = original.extras;
            WritableMap notif = Arguments.createMap();
            notif.putString("identifier", "" + notification.getId());
            notif.putString("title", extras.getString(Notification.EXTRA_TITLE));
            notif.putString("body", extras.getString(Notification.EXTRA_TEXT));
            notif.putString("tag", notification.getTag());
            notif.putString("group", original.getGroup());
            result.pushMap(notif);
        }
        
        return result;
        
    }
    
    public WritableArray getScheduledLocalNotifications() {
        WritableArray scheduled = Arguments.createArray();
        
        Map<String, ?> scheduledNotifications = scheduledNotificationsPersistence.getAll();
        
        for (Map.Entry<String, ?> entry : scheduledNotifications.entrySet()) {
            try {
                RNPushNotificationAttributes notification = fromJson(entry.getValue().toString());
                WritableMap notificationMap = Arguments.createMap();
                
                notificationMap.putString("title", notification.getTitle());
                notificationMap.putString("message", notification.getMessage());
                notificationMap.putString("number", notification.getNumber());
                notificationMap.putDouble("date", notification.getFireDate());
                notificationMap.putString("id", notification.getId());
                notificationMap.putString("repeatInterval", notification.getRepeatType());
                notificationMap.putString("soundName", notification.getSound());
                notificationMap.putString("data", notification.getUserInfo());
                
                scheduled.pushMap(notificationMap);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        
        return scheduled;
    }
    
    public void cancelAllScheduledNotifications() {
        Log.i(LOG_TAG, "Cancelling all notifications");
        
        for (String id : scheduledNotificationsPersistence.getAll().keySet()) {
            cancelScheduledNotification(id);
        }
    }
    
    public void cancelScheduledNotification(String notificationIDString) {
        Log.i(LOG_TAG, "Cancelling notification: " + notificationIDString);
        
        // remove it from the alarm manger schedule
        Bundle b = new Bundle();
        b.putString("id", notificationIDString);
        PendingIntent pendingIntent = toScheduleNotificationIntent(b);
        
        if (pendingIntent != null) {
            getAlarmManager().cancel(pendingIntent);
        }
        
        if (scheduledNotificationsPersistence.contains(notificationIDString)) {
            // remove it from local storage
            SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
            editor.remove(notificationIDString);
            editor.apply();
        } else {
            Log.w(LOG_TAG, "Unable to find notification " + notificationIDString);
        }
        
        // removed it from the notification center
        NotificationManager notificationManager = notificationManager();
        
        try {
            notificationManager.cancel(Integer.parseInt(notificationIDString));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to parse Notification ID " + notificationIDString, e);
        }
    }
    
    private NotificationManager notificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
    
    public List<String> listChannels() {
        List<String> channels = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return channels;
        
        NotificationManager manager = notificationManager();
        
        if (manager == null)
            return channels;
        
        List<NotificationChannel> listChannels = manager.getNotificationChannels();
        
        for(NotificationChannel channel : listChannels) {
            channels.add(channel.getId());
        }
        
        return channels;
    }
    
    public boolean channelBlocked(String channel_id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false;
        
        NotificationManager manager = notificationManager();
        
        if (manager == null)
            return false;
        
        NotificationChannel channel = manager.getNotificationChannel(channel_id);
        
        if(channel == null)
            return false;
        
        return NotificationManager.IMPORTANCE_NONE == channel.getImportance();
    }
    
    public boolean channelExists(String channel_id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false;
        
        NotificationManager manager = notificationManager();
        
        if (manager == null)
            return false;
        
        NotificationChannel channel = manager.getNotificationChannel(channel_id);
        
        return channel != null;
    }
    
    public void deleteChannel(String channel_id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        
        NotificationManager manager = notificationManager();
        
        if (manager == null)
            return;
        
        manager.deleteNotificationChannel(channel_id);
    }
    
    private boolean checkOrCreateChannel(NotificationManager manager, String channel_id, String channel_name, String channel_description, Uri soundUri, int importance, long[] vibratePattern) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false;
        if (manager == null)
            return false;
        
        NotificationChannel channel = manager.getNotificationChannel(channel_id);
        
        if (
            channel == null && channel_name != null && channel_description != null ||
                channel != null &&
                    (
                        channel_name != null && !channel_name.equals(channel.getName()) ||
                            channel_description != null && !channel_description.equals(channel.getDescription())
                    )
        ) {
            // If channel doesn't exist create a new one.
            // If channel name or description is updated then update the existing channel.
            channel = new NotificationChannel(channel_id, channel_name, importance);
            
            channel.setDescription(channel_description);
            channel.enableLights(true);
            channel.enableVibration(vibratePattern != null);
            channel.setVibrationPattern(vibratePattern);
            
            if (soundUri != null) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                                      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                      .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                                      .build();
                
                channel.setSound(soundUri, audioAttributes);
            } else {
                channel.setSound(null, null);
            }
            
            manager.createNotificationChannel(channel);
            
            return true;
        }
        
        return false;
    }
    
    public boolean createChannel(ReadableMap channelInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false;
        
        String channelId = channelInfo.getString("channelId");
        String channelName = channelInfo.getString("channelName");
        String channelDescription = channelInfo.hasKey("channelDescription") ? channelInfo.getString("channelDescription") : "";
        boolean playSound = !channelInfo.hasKey("playSound") || channelInfo.getBoolean("playSound");
        String soundName = channelInfo.hasKey("soundName") ? channelInfo.getString("soundName") : "default";
        int importance = channelInfo.hasKey("importance") ? channelInfo.getInt("importance") : 4;
        boolean vibrate = channelInfo.hasKey("vibrate") && channelInfo.getBoolean("vibrate");
        long[] vibratePattern = vibrate ? new long[] { 0, DEFAULT_VIBRATION } : null;
        
        NotificationManager manager = notificationManager();
        
        Uri soundUri = playSound ? getSoundUri(soundName) : null;
        
        return checkOrCreateChannel(manager, channelId, channelName, channelDescription, soundUri, importance, vibratePattern);
    }
    
    public boolean isApplicationInForeground() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos != null) {
            for (RunningAppProcessInfo processInfo : processInfos) {
                if (processInfo.processName.equals(context.getPackageName())
                        && processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && processInfo.pkgList.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
