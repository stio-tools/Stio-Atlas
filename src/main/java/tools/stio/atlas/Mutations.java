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
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.List;

import tools.stio.atlas.Atlas.Controller;
import tools.stio.atlas.Atlas.Tools;
import tools.stio.atlas.Dt.Log;

/**
 * @author Oleg Orlov
 * @since  24 Apr 2016
 */
public class Mutations {
    private static final boolean debug = false;
    private static final String TAG = Mutations.class.getSimpleName();
    private Activity activity;

    private List<Mutations.Mutation> mutations = new ArrayList<Mutations.Mutation>();

    List<Mutations.MutationListener> listeners = new ArrayList<Mutations.MutationListener>();

    public Mutations(Activity activity) {
        this.activity = activity;
    }

    public void popMutation() {
        Mutations.Mutation last = mutations.remove(mutations.size() - 1);
        last.reverse();
    }

    public void popAllMutations(Mutations.Mutation... but) {
        for (int i = mutations.size() - 1 ; i >= 0; i--) {

            if (Tools.contains(but, mutations.get(i))) continue;

            Mutations.Mutation m = mutations.remove(i);
            if (i == mutations.size() - 1) {
                m.reverse();
            } else {
                m.reverseNoAnimations();
            }
        }
    }

    public void injectAndPopMutations(Controller fragToInject) {

        Mutations.Mutation m = mutations.get(0);

        View newView = fragToInject.onCreateView(activity.getLayoutInflater(), m.container, null);

        Mutations.Mutation atRight    = new Mutation(this);
        atRight.animEnterId = m.animEnterId;
        atRight.animExitId  = m.animExitId;
        atRight.animHideId  = m.animHideId;
        atRight.animReturnId = m.animReturnId;
        atRight.container   = m.container;

        atRight.workView    = m.workView;
        atRight.viewToReplace = newView;

        m.workView = newView;

        // pretend mutation was "performed in the past"
        int index = Tools.findChildIndex(m.workView, m.container);
        m.container.addView(newView, index);

        mutations.add(1, atRight);

        for (int i = mutations.size() - 1 ; i >= 1; i--) {
            Mutations.Mutation mi = mutations.remove(i);
            if (i == mutations.size() - 1) {
                mi.reverse();
            } else {
                mi.reverseNoAnimations();
            }
        }
    }

    private void firePerformBefore(Mutations.Mutation m) {
        if (debug) Log.w(TAG, "firePerformBefore() m: " + m);
        if (m.listener != null) m.listener.beforePerform(m.workView, m.container, m.viewToReplace, m);
        for (int i = 0 ; i < listeners.size(); i++) {
            listeners.get(i).beforePerform(m.workView, m.container, m.viewToReplace, m);
        }
    }

    private void firePerformAfter(Mutations.Mutation m) {
        if (debug) Log.w(TAG, "firePerformAfter() m: " + m);
        if (m.listener != null) m.listener.afterPerform(m.workView, m.container, m.viewToReplace, m);
        for (int i = 0 ; i < listeners.size(); i++) {
            listeners.get(i).afterPerform(m.workView, m.container, m.viewToReplace, m);
        }

    }

    private void fireReverseBefore(Mutations.Mutation m) {
        if (debug) Log.w(TAG, "fireReverseBefore() m: " + m);
        if (m.listener != null) m.listener.beforeReverse(m.workView, m.container, m.viewToReplace, m);
        for (int i = 0 ; i < listeners.size(); i++) {
            listeners.get(i).beforeReverse(m.workView, m.container, m.viewToReplace, m);
        }
    }

    private void fireReverseComplete(Mutations.Mutation m) {
        if (debug) Log.w(TAG, "fireReverseComplete() m: " + m);
        if (m.listener != null) m.listener.afterReverse(m.workView, m.container, m.viewToReplace, m);
        for (int i = 0 ; i < listeners.size(); i++) {
            listeners.get(i).afterReverse(m.workView, m.container, m.viewToReplace, m);
        }
    }

    public int size() {
        return mutations.size();
    }

    public Mutations.Mutation get(int at) {
        return mutations.get(at);
    }

    public void addListener(MutationListener listener) {
        listeners.add(listener);
    }

    public Activity getActivity() {
        return activity;
    }

    public static class MutationListener {
        /**
         * @param workView  - fragment root view
         * @param container - where workView was added
         * @param viewToReplace - view replaced by workView
         */
        public void beforePerform(View workView, View container, View viewToReplace, Mutations.Mutation m) {
        }
        /**
         * @param workView  - fragment root view
         * @param container - where workView was added
         * @param viewToReplace - view replaced by workView
         */
        public void afterPerform(View workView, View container, View viewToReplace, Mutations.Mutation m) {
        }
        /**
         * @param workView  - fragment root view
         * @param container - where workView was added
         * @param viewToReplace - view replaced by workView
         */
        public void beforeReverse(View workView, View container, View viewToReplace, Mutations.Mutation m) {
        }
        /**
         * @param workView  - fragment root view
         * @param container - where workView was added
         * @param viewToReplace - view replaced by workView
         */
        public void afterReverse(View workView, View container, View viewToReplace, Mutations.Mutation m) {
        }
    }

    public static class Mutation implements Runnable {

        private Mutations engine;

        Activity activity;
        Controller fragment;

        Object id;

        private ViewGroup container;
        private View workView;
        private View viewToReplace;

        int containerId;
        int replaceViewId;

        int animEnterId;
        int animExitId;
        int animHideId;
        int animReturnId;

        Mutations.MutationListener listener = null;

        public static Mutations.Mutation on(Mutations engine) {
            Mutations.Mutation result = new Mutation(engine);
            return result;
        }

        private Mutation(Mutations engine) {
            this.engine = engine;
            this.activity = engine.activity;
        }

        public void run() {
            if (workView == null) perform();
            else reverse();
        }

        public void perform() {
            if (container == null && containerId == 0) throw new IllegalStateException("Container must be specified");
            if (container == null) {
                container = (ViewGroup) activity.findViewById(containerId);
            }
            if (container == null) {
                throw new IllegalStateException("Cannot find container with id " + Tools.findField(containerId, R.id.class) + ": " + containerId);
            }

            if (viewToReplace == null) {
                viewToReplace = Tools.findChildById(container, replaceViewId);
            }
            if (viewToReplace == null && container.getChildCount() > 0) {
                if (replaceViewId != 0) Log.e(TAG, "perform() cannot find view to replace with id : "
                    + Tools.findField(replaceViewId, R.id.class) + ": " + replaceViewId
                    + " in " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
                viewToReplace = container.getChildAt(container.getChildCount() - 1);
            }

            this.workView = fragment.onCreateView(activity.getLayoutInflater(), container, null);
            if (workView == null) throw new IllegalArgumentException("Fragment with no view, really? fragment: " + fragment);

            final Animation entering = animEnterId != 0 ? AnimationUtils.loadAnimation(activity, animEnterId) : null;
            final Animation hiding  = animHideId != 0 ? AnimationUtils.loadAnimation(activity, animHideId) : null;

            int anims = 0;
            if (entering != null) anims++;
            if (hiding   != null && viewToReplace != null) anims++;
            final int totalHits = anims;
            final int[] hits = new int[] {0};

            engine.firePerformBefore(this);

            // init
            workView.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
                public boolean onPreDraw() {
                    workView.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (entering != null) entering.setAnimationListener(new Tools.AnimationAdapter() {
                        public void onAnimationEnd(Animation animation) {
                            animation.setAnimationListener(null);
                            if (debug) Log.w(TAG, "perform() entering ended");
                            hits[0]++;
                            if (hits[0] == totalHits) onComplete(workView, viewToReplace, container);
                        }
                    });
                    if (hiding != null && viewToReplace != null) hiding.setAnimationListener(new Tools.AnimationAdapter() {
                        public void onAnimationEnd(Animation animation) {
                            if (debug) Log.w(TAG, "perform() exiting ended");
                            animation.setAnimationListener(null);
                            hits[0]++;
                            if (hits[0] == totalHits) onComplete(workView, viewToReplace, container);
                        }
                    });
                    if (entering != null)                   workView.startAnimation(entering);
                    if (hiding != null && viewToReplace != null)  viewToReplace.startAnimation(hiding);

                    if (hits[0] == totalHits) onComplete(workView, viewToReplace, container);
                    return true;
                }
            });

            if (debug) Log.w(TAG, "perform()    before: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
            // register mutation
            engine.mutations.add(this);

            // start
            int viewToReplacePosition = Tools.getViewPosition(container, viewToReplace);
            int workViewPosition = viewToReplacePosition == -1 ? container.getChildCount() - 1 : viewToReplacePosition + 1;
            if (debug) Log.w(TAG, "perform()     added: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
            container.addView(workView, workViewPosition);
        }

        protected void onComplete(final View workView, final View viewToReplace, final ViewGroup container) {
            this.workView.post(new Runnable() {
                public void run() {
                    if (viewToReplace != null) viewToReplace.setVisibility(View.GONE);
                    if (debug) Log.w(TAG, "onComplete()  after: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
                    engine.firePerformAfter(Mutation.this);
                }
            });
        }

        private void reverse() {
            if (workView == null) throw new IllegalStateException("Cannot reverse, it never performed");

            if (viewToReplace == null) {        // do not need to request layout in order to animate
                reverseImpl();
            } else {
                // arm preDraw listener
                viewToReplace.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
                    public boolean onPreDraw() {
                        viewToReplace.getViewTreeObserver().removeOnPreDrawListener(this);
                        reverseImpl();
                        return true;
                    }
                });
                // start
                if (viewToReplace.getVisibility() == View.VISIBLE) {
                    viewToReplace.requestLayout();
                } else {
                    viewToReplace.setVisibility(View.VISIBLE);
                    viewToReplace.requestLayout();
                }
            }
        }

        private void reverseImpl() {
            if (debug) Log.w(TAG, "reverseImpl()   before: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
            if (workView == null) throw new IllegalStateException("Cannot reverse, it never performed");

            engine.fireReverseBefore(this);

            final Animation exiting   = animExitId    != 0 ? AnimationUtils.loadAnimation(activity, animExitId) : null;
            final Animation returning = animReturnId  != 0 ? AnimationUtils.loadAnimation(activity, animReturnId) : null;
            int anims = 0;
            if (exiting != null) anims++;
            if (returning != null && viewToReplace != null) anims++;
            final int totalHits = anims;
            final int[] hits = new int[] {0};

            if (exiting != null) exiting.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    if (debug) Log.d(TAG, "reverseImpl() exiting started");
                }
                public void onAnimationRepeat(Animation animation) {}
                public void onAnimationEnd(Animation animation) {
                    animation.setAnimationListener(null);
                    if (debug) Log.d(TAG, "reverseImpl() exiting ended");
                    hits[0]++;
                    if (hits[0] == totalHits) onReverseComplete(workView, viewToReplace, container);
                }
            });
            if (returning != null) returning.setAnimationListener(new AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    if (debug) Log.d(TAG, "reverseImpl() returning started");
                }
                public void onAnimationRepeat(Animation animation) {}
                public void onAnimationEnd(Animation animation) {
                    if (debug) Log.d(TAG, "reverseImpl() returning ended");
                    animation.setAnimationListener(null);
                    hits[0]++;
                    if (hits[0] == totalHits) onReverseComplete(workView, viewToReplace, container);
                }
            });
            if (exiting != null)                            workView.startAnimation(exiting);
            if (returning != null && viewToReplace != null) viewToReplace.startAnimation(returning);

            if (hits[0] == totalHits) onReverseComplete(workView, viewToReplace, container);
        }

        private void reverseNoAnimations() {
            if (debug) Log.w(TAG, "reverseNoAnimations() before: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
            engine.fireReverseBefore(this);
            viewToReplace.setVisibility(View.VISIBLE);
            onReverseComplete(workView, viewToReplace, container);
        }

        protected void onReverseComplete(final View workView, View prevView, final ViewGroup container) {
            // otherwise crash
            workView.post(new Runnable() {
                public void run() {
                    container.removeView(workView);
                    if (debug) Log.w(TAG, "onReverseComplete()      after: " + Tools.VIEW_GROUP.toString(container) + Dt.toString(container, "\n", "\n", true, Tools.VIEW_GROUP));
                    engine.fireReverseComplete(Mutation.this);
                }
            });
        }

        //
        //
        //

        public Mutations.Mutation add(Controller f) {
            this.fragment = f;
            return this;
        }

        public Mutations.Mutation into(int containerId) {
            this.containerId = containerId;
            return this;
        }

        public Mutations.Mutation into(View container) {
            if (!(container instanceof ViewGroup)) throw new IllegalArgumentException("required ViewGroup is null");
            this.container = (ViewGroup) container;
            return this;
        }

        public Mutations.Mutation replace(int replaceViewId) {
            this.replaceViewId = replaceViewId;
            return this;
        }

        public Mutations.Mutation replace(View viewToReplace) {
            this.viewToReplace = viewToReplace;
            return this;
        }

        public Mutations.Mutation withId(Object id) {
            this.id = id;
            return this;
        }

        public Object getId() {
            return id;
        }

        public ViewGroup getContainer() {
            return container;
        }

        /**
         * @param newViewEnterAnimId - how to animate entering
         * @param oldViewHideAnimId - how to animate underlying view
         */
        public Mutations.Mutation animateEnter(int newViewEnterAnimId, int oldViewHideAnimId) {
            this.animEnterId = newViewEnterAnimId;
            this.animHideId = oldViewHideAnimId;
            return this;
        }

        /**
         * @param newViewExitAnimId - how to animate exiting
         * @param oldViewReturnAnimId - how to animate underlying view
         */
        public Mutations.Mutation animateExit(int newViewExitAnimId, int oldViewReturnAnimId) {
            this.animExitId = newViewExitAnimId;
            this.animReturnId = oldViewReturnAnimId;
            return this;
        }

        public Mutations.Mutation notify(Mutations.MutationListener listener) {
            this.listener = listener;
            return this;
        }

    }

}