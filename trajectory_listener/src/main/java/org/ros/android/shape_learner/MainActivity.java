/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.shape_learner;

import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.ros.address.InetAddressFactory;
import org.ros.android.MessageCallable;
import org.ros.android.RosActivity;
import org.ros.android.android_tutorial_pubsub.R;
import org.ros.android.view.RosImageView;
import org.ros.message.Duration;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.time.NtpTimeProvider;
import java.util.concurrent.TimeUnit;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.util.List;

import geometry_msgs.PoseStamped;

import nav_msgs.Path;
/**
 * @author damonkohler@google.com (Damon Kohler). modified by Deanna Hood.
 */


public class MainActivity extends RosActivity {
    private InteractionManager interactionManager;
    private static final java.lang.String TAG = "trajectoryListener";
    private int timeoutDuration_mSecs = -1; //time in ms to leave the trajectory displayed before removing it (negative displays indefinitely)
    private double PPI_tablet = 298.9; //pixels per inch of android tablet
    private int[] resolution_tablet = {2560, 1600};
    private double MM2INCH = 0.0393701; //number of millimetres in one inch (for conversions)
    private DisplayManager<nav_msgs.Path> displayManager;
    private Button buttonClear;
    private GestureDetector gestureDetector;
    private boolean longClicked = true;
    public MainActivity() {
    // The RosActivity constructor configures the notification title and ticker
    // messages.
    super("Trajectory listener", "Trajectory listener");
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
      Log.e(TAG,String.valueOf(Build.VERSION.SDK_INT));
     // if (Build.VERSION.SDK_INT < 16) {
          getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                  WindowManager.LayoutParams.FLAG_FULLSCREEN);
          Log.e(TAG,"Should be fullscreen now");
      //}
      setContentView(R.layout.main);
      buttonClear = (Button)findViewById(R.id.buttonClear);
      buttonClear.setOnClickListener(clearListener); // Register the onClick listener with the implementation above




      displayManager = (DisplayManager<nav_msgs.Path>) findViewById(R.id.image);
      displayManager.setTopicName("write_traj");
      displayManager.setMessageType(nav_msgs.Path._TYPE);

      displayManager.setMessageToDrawableCallable(new MessageCallable<Drawable, nav_msgs.Path>() {
        @Override
        public Drawable call(nav_msgs.Path message) {
            double[] shapeCentre_offset = {00.0,00.0};
            ShapeDrawable blankShapeDrawable = new ShapeDrawable(new PathShape(new android.graphics.Path(), 0, 0));
            blankShapeDrawable.setIntrinsicHeight(displayManager.getHeight());
            blankShapeDrawable.setIntrinsicWidth(displayManager.getWidth());
            blankShapeDrawable.setBounds(0, 0, displayManager.getWidth(), displayManager.getHeight());

            List<PoseStamped> points = message.getPoses();
            AnimationDrawable animationDrawable = new AnimationDrawable();

            android.graphics.Path trajPath = new android.graphics.Path();
            trajPath.moveTo((float) (M2PX(points.get(0).getPose().getPosition().getX()) + shapeCentre_offset[0]), resolution_tablet[1] - (float) (M2PX(points.get(0).getPose().getPosition().getY()) + shapeCentre_offset[1]));

            long timeUntilFirstFrame_msecs = Math.round(points.get(0).getHeader().getStamp().totalNsecs() / 1000000.0);
            animationDrawable.addFrame(blankShapeDrawable, (int) timeUntilFirstFrame_msecs);
            int totalTime = (int)timeUntilFirstFrame_msecs;

            for (int i = 0; i < points.size() - 1; i++) //special case for last point/frame of trajectory
            {
                //add new trajectory point onto path and create ShapeDrawable to pass as a frame for animation
                PoseStamped p = points.get(i);
                geometry_msgs.Point tx = p.getPose().getPosition();
                geometry_msgs.Point tx_next = points.get(i+1).getPose().getPosition();
                ShapeDrawable shapeDrawable = addPointToShapeDrawablePath_quad((float) (shapeCentre_offset[0]+M2PX(tx.getX())), resolution_tablet[1] - (float) (shapeCentre_offset[1]+M2PX(tx.getY())), (float) (shapeCentre_offset[0]+M2PX(tx_next.getX())), resolution_tablet[1] - (float) (shapeCentre_offset[1]+M2PX(tx_next.getY())), trajPath);

                //determine the duration of the frame for the animation
                Duration frameDuration = points.get(i + 1).getHeader().getStamp().subtract(p.getHeader().getStamp()); // take difference between times to get appropriate duration for frame to be displayed

                long dt_msecs = Math.round(frameDuration.totalNsecs() / 1000000.0);
                animationDrawable.addFrame(shapeDrawable, (int) dt_msecs); //unless the duration is over 2mil seconds the cast is ok
                totalTime+=(int)dt_msecs;
            }
            //cover end case
            PoseStamped p = points.get(points.size() - 1);
            geometry_msgs.Point tx = p.getPose().getPosition();
            float pointToAdd_x = (float) (M2PX(tx.getX()) + shapeCentre_offset[0]);
            float pointToAdd_y = resolution_tablet[1] - (float) (M2PX(tx.getY()) + shapeCentre_offset[1]);
            ShapeDrawable shapeDrawable = addPointToShapeDrawablePath(pointToAdd_x, pointToAdd_y, trajPath);


            if (timeoutDuration_mSecs >= 0)//only display the last frame until timeoutDuration has elapsed
            {
                animationDrawable.addFrame(shapeDrawable, timeoutDuration_mSecs);
                animationDrawable.addFrame(blankShapeDrawable, 0); //stop displaying
            } else { //display last frame indefinitely
                animationDrawable.addFrame(shapeDrawable, 1000); //think it will be left there until something clears it so time shouldn't matter
            }
            Log.e(TAG,"Path to draw: " + String.valueOf(trajPath));
            Log.e(TAG,"Total time (in theory): " + String.valueOf(totalTime));
            animationDrawable.setBounds(0, 0, displayManager.getWidth(), displayManager.getHeight());
            animationDrawable.setOneShot(true); //do not auto-restart the animation

            // Pass our animation drawable to drawable class with callback on finish
            AnimationDrawableWithEndCallback animationDrawableWithEndCallback = new AnimationDrawableWithEndCallback(animationDrawable) {
                @Override
                void onAnimationFinish() {
                    // Animation has finished...
                    onShapeDrawingFinish();
                }
            };
            return animationDrawableWithEndCallback;
        }
    });


      gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
          @Override
          public void onLongPress(MotionEvent e) {
              float x = e.getX();
              float y = e.getY();
              Log.e(TAG, "Double tap at: ["+String.valueOf(x)+", "+String.valueOf(y)+"]");
              //publish touch event in world coordinates instead of tablet coordinates
              interactionManager.publishGestureInfoMessage(PX2M(x), PX2M(resolution_tablet[1] - y));
              longClicked = true;
          }
      });
  }

private void onShapeDrawingFinish(){
    Log.e(TAG,"Animation finished!");
    displayManager.publishShapeFinishedMessage();
}

private View.OnClickListener clearListener = new View.OnClickListener() {
    public void onClick(View v) {
        Log.e(TAG,"onClick() called - clear button");
        interactionManager.publishClearScreenMessage();
    }
};

private ShapeDrawable addPointToShapeDrawablePath(float x, float y, android.graphics.Path path){
// add point to path
    path.lineTo(x,y);

    // make local copy of path and store in new ShapeDrawable
    android.graphics.Path currPath = new android.graphics.Path(path);

    ShapeDrawable shapeDrawable = new ShapeDrawable();
    shapeDrawable.getPaint().setColor(Color.RED);
    shapeDrawable.getPaint().setStyle(Paint.Style.STROKE);
    shapeDrawable.getPaint().setStrokeWidth(10);
    shapeDrawable.getPaint().setStrokeJoin(Paint.Join.ROUND);
    shapeDrawable.getPaint().setStrokeCap(Paint.Cap.ROUND);
    shapeDrawable.getPaint().setPathEffect(new CornerPathEffect(30));
    shapeDrawable.getPaint().setAntiAlias(true);          // set anti alias so it smooths
    shapeDrawable.setIntrinsicHeight(displayManager.getHeight());
    shapeDrawable.setIntrinsicWidth(displayManager.getWidth());
    shapeDrawable.setBounds(0, 0, displayManager.getWidth(), displayManager.getHeight());

    shapeDrawable.setShape(new PathShape(currPath,displayManager.getWidth(),displayManager.getHeight()));

    return shapeDrawable;
}

private ShapeDrawable addPointToShapeDrawablePath_quad(float x, float y, float x_next, float y_next, android.graphics.Path path){
// add point to path using quadratic bezier curve
    path.quadTo(x,y,(x_next+x)/2,(y_next+y)/2);

    // make local copy of path and store in new ShapeDrawable
    android.graphics.Path currPath = new android.graphics.Path(path);

    ShapeDrawable shapeDrawable = new ShapeDrawable();
    shapeDrawable.getPaint().setColor(Color.RED);
    shapeDrawable.getPaint().setStyle(Paint.Style.STROKE);
    shapeDrawable.getPaint().setStrokeWidth(10);
    shapeDrawable.getPaint().setStrokeJoin(Paint.Join.ROUND);
    shapeDrawable.getPaint().setStrokeCap(Paint.Cap.ROUND);
    shapeDrawable.getPaint().setPathEffect(new CornerPathEffect(30));
    shapeDrawable.getPaint().setAntiAlias(true);          // set anti alias so it smooths
    shapeDrawable.setIntrinsicHeight(displayManager.getHeight());
    shapeDrawable.setIntrinsicWidth(displayManager.getWidth());
    shapeDrawable.setBounds(0, 0, displayManager.getWidth(), displayManager.getHeight());

    shapeDrawable.setShape(new PathShape(currPath,displayManager.getWidth(),displayManager.getHeight()));

    return shapeDrawable;
    }


private double MM2PX(double x){ return x*MM2INCH*PPI_tablet; }
private double PX2MM(double x){return x/(PPI_tablet*MM2INCH);}

private double M2PX(double x){return (MM2PX(x)*1000.0);}
private double PX2M(double x){return PX2MM(x)/1000.0;}



    @Override
  protected void init(NodeMainExecutor nodeMainExecutor) {
        interactionManager = new InteractionManager();
        interactionManager.setTouchInfoTopicName("touch_info");
        interactionManager.setGestureInfoTopicName("long_touch_info");
        interactionManager.setClearScreenTopicName("clear_screen");
        displayManager.setClearScreenTopicName("clear_screen");
        displayManager.setFinishedShapeTopicName("shape_finished");

        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
    // At this point, the user has already been prompted to either enter the URI
    // of a master to use or to start a master locally.
    nodeConfiguration.setMasterUri(getMasterUri());

        NtpTimeProvider ntpTimeProvider = new NtpTimeProvider(InetAddressFactory.newFromHostString("192.168.1.5"),nodeMainExecutor.getScheduledExecutorService());
        ntpTimeProvider.startPeriodicUpdates(1, TimeUnit.MINUTES);
        nodeConfiguration.setTimeProvider(ntpTimeProvider);


        // The RosTextView is a NodeMain that must be executed in order to
    // start displaying incoming messages.
      Log.e(TAG, "Ready to execute");
    nodeMainExecutor.execute(displayManager, nodeConfiguration.setNodeName("android_gingerbread/trajectory_listener"));
    nodeMainExecutor.execute(interactionManager, nodeConfiguration.setNodeName("android_gingerbread/interaction_manager"));

  }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                longClicked = false;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                if(!longClicked){
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    Log.e(TAG, "Touch at: ["+String.valueOf(x)+", "+String.valueOf(y)+"]");
                    //publish touch event in world coordinates instead of tablet coordinates
                    interactionManager.publishTouchInfoMessage(PX2M(x), PX2M(resolution_tablet[1] - y));
                }
                break;
        }


        return true;
    }
}




