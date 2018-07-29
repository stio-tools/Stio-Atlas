/*
 * Copyright (c) 2016 Oleg Orlov. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.stio.atlas;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.HashMap;
import java.util.List;

import tools.stio.atlas.Atlas.Controller;
import tools.stio.atlas.Dt.Log;

/**
 * @author Oleg Orlov
 * @since  08 Jan 2016
 */
public class ScreenBlank extends Activity {
    private final static String TAG = ScreenBlank.class.getSimpleName();
    private final static boolean debug = false;

    private static final int DURATION_OPENING = 666;
    private static final int DURATION_CLOSING = 333;

    private static final String ACTIVITY_KEY = "#activityKey";

    private Controller2 controllerImpl;

    private ViewGroup rootView;
    private View backgroundView;

    /** to understand if we should clean up data at onDestroy() or keep it. Helps when orientation changes */
    private boolean finished = false;

    private static boolean animate = false;

    private static final HashMap<String, ActivityEntry> activityData = new HashMap<String, ActivityEntry>();
    private static int activityId = 0;
    private static Class activityClass;

    /** @param controller - Fragment or {@link Controller}*/
    public static void open(Object controller, final Activity activity) {
        open(controller, (controller instanceof Controller2 ? (Controller2) controller : null), null, activity);
    }

    /**
     * @param controller - Fragment or Controller to display
     * @param controller2 - your impl of Controller2 to get total control of ScreenBlank
     * @param data - any data you want to access later using {@link ScreenBlank#getData()}
     */
    public static void open(Object controller, Controller2 controller2, Object data, final Activity activity) {
        if (controller2 == null && !(controller instanceof Controller) && !(controller instanceof Controller2)) {
            throw new IllegalArgumentException("Only Controller is supported as first argument. Controller: " + controller + ", controller2: " + controller2);
        }

        Class<ScreenBlank> screenBlankClass = activityClass == null ? ScreenBlank.class : activityClass;
        final Intent imgIntent = new Intent(activity, screenBlankClass);
        int id = activityId++;
        String key = "" + screenBlankClass + "." + id;
        imgIntent.putExtra(ACTIVITY_KEY, key);

        ActivityEntry entry = new ActivityEntry();
        entry.controller = controller;
        entry.controller2 = controller2;
        entry.data = data;

        activityData.put(key, entry);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                activity.startActivity(imgIntent);
                if (animate) activity.overridePendingTransition(0, 0);
            }
        });
    }
    public static void setActivityClass(Class activityClass) {
        ScreenBlank.activityClass = activityClass;
    }

    private String getDataKey() {
        return getIntent().getStringExtra(ACTIVITY_KEY);
    }

    private static class ActivityEntry {
        Object controller;
        Controller2 controller2;
        Object data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (debug) Log.w(TAG, "onCreate() savedInstanceState: " + savedInstanceState);
        super.onCreate(savedInstanceState);

        ActivityEntry entry = activityData.get(getDataKey());
        Object controller = entry.controller;

        if (entry.controller2 != null) {
            if (entry.controller2 instanceof Controller2Impl) {
                ((Controller2Impl)entry.controller2).activity = this;
            }
            this.controllerImpl = entry.controller2;
            this.controllerImpl.onCreate(this, savedInstanceState);

        } else {    /** Controller or Fragment*/

            setContentView(R.layout.screen_blank);

            this.rootView = (ViewGroup) findViewById(R.id.screen_blank_root);
            this.backgroundView = findViewById(R.id.screen_blank_background);

            // setup animation trigger
            this.rootView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    rootView.getViewTreeObserver().removeOnPreDrawListener(this);
                    performEnterAnimation();
                    return true;
                }
            });

            if (controller instanceof Controller) {
                Controller c = (Controller) controller;
                View content = c.onCreateView(getLayoutInflater(), rootView, null);
                rootView.addView(content);
            }

        }

    }

    /** @return data object passed to {@link ScreenBlank#open(Object controller, Controller2, Object data, Activity)} */
    public Object getData() {
        return activityData.get(getDataKey()).data;
    }

    // animate in
    private void performEnterAnimation() {
        if (!animate) return;
        rootView.setAlpha(0);
        rootView.animate().alpha(1).setDuration(DURATION_OPENING).setInterpolator(new AccelerateInterpolator());
    }

    // animate out
    private void performExitAnimation() {
        if (!animate) {
            finish();
            return;
        }

        long duration = DURATION_CLOSING;
        backgroundView.animate().alpha(0).setDuration(duration).setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
                public void run() {
                    finish();
                }
            });
    }

    @Override
    public void onBackPressed() {
        if (controllerImpl != null) {
            controllerImpl.onBackPressed(this);
        } else {
            performExitAnimation();
        }
    }

    @Override
    public void finish() {
        finished = true;
        super.finish();
        if (controllerImpl != null) {
            controllerImpl.onFinish(this);
        } else {
            if (animate) overridePendingTransition(0, 0);
        }
    }

    protected void onDestroy() {
        if (controllerImpl != null) {
            controllerImpl.onDestroy(this);
        }
        if (finished) {
            Log.w(TAG, "onDestroy() finished, cleaning activityData");
            activityData.remove(getDataKey());
        }
        super.onDestroy();
    }

    protected void onStart() {
        if (controllerImpl != null) {
            controllerImpl.onStart(this);
        }
        super.onStart();
    }

    protected void onResume() {
        if (controllerImpl != null) {
            controllerImpl.onResume(this);
        }
        super.onResume();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (controllerImpl != null) {
            controllerImpl.onConfigurationChanged(this, newConfig);
        }
        super.onConfigurationChanged(newConfig);
    }

    protected void onPause() {
        if (controllerImpl != null) {
            controllerImpl.onPause(this);
        }
        super.onPause();
    }

    protected void onStop() {
        if (controllerImpl != null) {
            controllerImpl.onStop(this);
        }
        super.onStop();
    }

    public interface Controller2 {
        public void onCreate(ScreenBlank blankActivity, Bundle savedInstanceState);
        public void onStart(ScreenBlank blankActivity);
        public void onResume(ScreenBlank blankActivity);
        public void onConfigurationChanged(ScreenBlank blankActivity, Configuration newConfig);
        public void onPause(ScreenBlank blankActivity);
        public void onStop(ScreenBlank blankActivity);
        public void onBackPressed(ScreenBlank blankActivity);
        public void onDestroy(ScreenBlank blankActivity);
        public void onFinish(ScreenBlank blankActivity);
    }

    /** supports more stuff related to {@link Activity} */
    public abstract static class Controller2Impl implements Controller2 {

        private ScreenBlank activity;

        public void finish(){
            activity.finish();
        }
        public ScreenBlank getActivity() {
            return activity;
        }

        public abstract void onCreate(ScreenBlank blankActivity, Bundle savedInstanceState);
        public void onStart(ScreenBlank blankActivity) {}
        public void onResume(ScreenBlank blankActivity) {}
        public void onConfigurationChanged(ScreenBlank blankActivity, Configuration newConfig) {}
        public void onPause(ScreenBlank blankActivity) {}
        public void onStop(ScreenBlank blankActivity) {}
        public void onBackPressed(ScreenBlank blankActivity) {blankActivity.finish();}
        public void onDestroy(ScreenBlank blankActivity) {}
        public void onFinish(ScreenBlank blankActivity) {}
    }

}