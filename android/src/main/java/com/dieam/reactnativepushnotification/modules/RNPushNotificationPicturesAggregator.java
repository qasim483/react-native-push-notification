// @custom
package com.dieam.reactnativepushnotification.modules;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import com.facebook.common.executors.CallerThreadExecutor;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.common.Priority;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

import android.util.Log;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.Nullable;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationPicturesAggregator {
  interface Callback {
    public void call(Bitmap largeIconImage, Bitmap bigPictureImage, Bitmap bigLargeIconImage);
  }
  
  private AtomicInteger count = new AtomicInteger(0);
  
  private Bitmap largeIconImage;
  private Bitmap bigPictureImage;
  private Bitmap bigLargeIconImage;
  
  private Callback callback;
  
  public RNPushNotificationPicturesAggregator(Callback callback) {
    this.callback = callback;
  }
  
  public void setBigPicture(Bitmap bitmap) {
    this.bigPictureImage = bitmap;
    this.finished();
  }
  
  public void setBigPictureUrl(Context context, String url) {
    if(null == url) {
      this.setBigPicture(null);
      return;
    }
    
    Uri uri = null;
    
    try {
      uri = Uri.parse(url);
    } catch(Exception ex) {
      Log.e(LOG_TAG, "Failed to parse bigPictureUrl", ex);
      this.setBigPicture(null);
      return;
    }
    
    final RNPushNotificationPicturesAggregator aggregator = this;
    
    this.downloadRequest(context, uri, new BaseBitmapDataSubscriber() {
      @Override
      public void onNewResultImpl(@Nullable Bitmap bitmap) {
        aggregator.setBigPicture(bitmap);
      }
      
      @Override
      public void onFailureImpl(DataSource dataSource) {
        aggregator.setBigPicture(null);
      }
    });
  }
  
  // QZ: CUSTOM FUNCTION TO MAKE CIRCULAR NOTIFICATION IMAGE.
  public Bitmap getCircleBitmap(Bitmap bitmap)
  {
    final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
        bitmap.getHeight(), Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(output);
    
    final int color = Color.RED;
    final Paint paint = new Paint ();
    final Rect rect = new Rect (0, 0, bitmap.getWidth(), bitmap.getHeight());
    final RectF rectF = new RectF (rect);
    
    paint.setAntiAlias (true);
    canvas.drawARGB (0, 0, 0, 0);
    paint.setColor (color);
    canvas.drawOval (rectF, paint);
    
    paint.setXfermode(new PorterDuffXfermode (PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(bitmap, rect, rect, paint);
    
    // IF BITMAP IS NOT RECYCLED THEN RECYCLE IT NOW.
    if (bitmap != null && !bitmap.isRecycled())
    {
      //bitmap.recycle();
      bitmap = null;
    }
    return output;
  }
  
  public void setLargeIcon(Bitmap bitmap)
  {
    this.largeIconImage = bitmap;
    this.finished();
  }
  
  public void setLargeIconUrl(Context context, String url) {
    if(null == url) {
      this.setLargeIcon(null);
      return;
    }
    
    Uri uri = null;
    
    try {
      uri = Uri.parse(url);
    } catch(Exception ex) {
      Log.e(LOG_TAG, "Failed to parse largeIconUrl", ex);
      this.setLargeIcon(null);
      return;
    }
    
    final RNPushNotificationPicturesAggregator aggregator = this;
    
    this.downloadRequest(context, uri, new BaseBitmapDataSubscriber() {
      @Override
      public void onNewResultImpl(@Nullable Bitmap bitmap) {
        // QZ: CUSTOM LINE ADDED.
        bitmap = aggregator.getCircleBitmap (bitmap);
        aggregator.setLargeIcon(bitmap);
      }
      
      @Override
      public void onFailureImpl(DataSource dataSource) {
        aggregator.setLargeIcon(null);
      }
    });
  }
  
  public void setBigLargeIcon(Bitmap bitmap) {
    this.bigLargeIconImage = bitmap;
    this.finished();
  }
  
  public void setBigLargeIconUrl(Context context, String url) {
    if(null == url) {
      this.setBigLargeIcon(null);
      return;
    }
    
    Uri uri = null;
    
    try {
      uri = Uri.parse(url);
    } catch(Exception ex) {
      Log.e(LOG_TAG, "Failed to parse bigLargeIconUrl", ex);
      this.setBigLargeIcon(null);
      return;
    }
    
    final RNPushNotificationPicturesAggregator aggregator = this;
    
    this.downloadRequest(context, uri, new BaseBitmapDataSubscriber() {
      @Override
      public void onNewResultImpl(@Nullable Bitmap bitmap) {
        aggregator.setBigLargeIcon(bitmap);
      }
      
      @Override
      public void onFailureImpl(DataSource dataSource) {
        aggregator.setBigLargeIcon(null);
      }
    });
  }
  
  private void downloadRequest(Context context, Uri uri, BaseBitmapDataSubscriber subscriber) {
    ImageRequest imageRequest = ImageRequestBuilder
                                    .newBuilderWithSource(uri)
                                    .setRequestPriority(Priority.HIGH)
                                    .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
                                    .build();
    
    if(!Fresco.hasBeenInitialized()) {
      Fresco.initialize(context);
    }
    
    DataSource<CloseableReference<CloseableImage>> dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, context);
    
    dataSource.subscribe(subscriber, CallerThreadExecutor.getInstance());
  }
  
  private void finished() {
    synchronized(this.count) {
      int val = this.count.incrementAndGet();
      
      if(val >= 3 && this.callback != null) {
        this.callback.call(this.largeIconImage, this.bigPictureImage, this.bigLargeIconImage);
      }
    }
  }
}