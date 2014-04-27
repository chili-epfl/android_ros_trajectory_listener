/*
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

import android.util.Log;

import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import geometry_msgs.PointStamped;
import std_msgs.Empty;

/**
 * Publishes touch events.
 *
 * @author Deanna Hood
 */

class InteractionManager extends AbstractNodeMain {
    private static final java.lang.String TAG = "InteractionManager";
    private String touchInfoTopicName;
    private ConnectedNode connectedNode;
    private Publisher<PointStamped> touchInfoPublisher;
    private Publisher<PointStamped> gestureInfoPublisher;
    private String gestureInfoTopicName;
    private Publisher<Empty> clearScreenPublisher;
    private String clearScreenTopicName;

    public void setTouchInfoTopicName(String topicName) {
        this.touchInfoTopicName = topicName;
    }
    public void setGestureInfoTopicName(String topicName) { this.gestureInfoTopicName = topicName; }
    public void setClearScreenTopicName(String topicName) {
        this.clearScreenTopicName = topicName;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("touch_publisher");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
         this.connectedNode = connectedNode;
        this.touchInfoPublisher =
                connectedNode.newPublisher(touchInfoTopicName, geometry_msgs.PointStamped._TYPE);
        this.gestureInfoPublisher =
                connectedNode.newPublisher(gestureInfoTopicName, geometry_msgs.PointStamped._TYPE);
        this.clearScreenPublisher =
                connectedNode.newPublisher(clearScreenTopicName, Empty._TYPE);

    }

  public void publishTouchInfoMessage(double x, double y) {

    geometry_msgs.PointStamped pointStamped = touchInfoPublisher.newMessage();
      pointStamped.getHeader().setStamp(connectedNode.getCurrentTime());
      pointStamped.getPoint().setX(x);
      pointStamped.getPoint().setY(y);

      touchInfoPublisher.publish(pointStamped);

  }
    public void publishGestureInfoMessage(double x, double y) {

        geometry_msgs.PointStamped pointStamped = gestureInfoPublisher.newMessage();
        pointStamped.getHeader().setStamp(connectedNode.getCurrentTime());
        pointStamped.getPoint().setX(x);
        pointStamped.getPoint().setY(y);

        gestureInfoPublisher.publish(pointStamped);

    }
    public void publishClearScreenMessage(){
        Log.e(TAG, "Publishing clear screen request");
        Empty message = clearScreenPublisher.newMessage();
        clearScreenPublisher.publish(message);
    }
}