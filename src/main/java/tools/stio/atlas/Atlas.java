/*
 * Copyright (c) 2015 Oleg Orlov. All rights reserved.
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import tools.stio.atlas.Dt.AdapterOnItem;
import tools.stio.atlas.Dt.Log;

/**
 * @author Oleg Orlov
 * @since 12 May 2015
 */
public class Atlas {

    public static final String METADATA_KEY_CONVERSATION_TITLE = "conversationName";
    
    public static final String MIME_TYPE_ATLAS_LOCATION = "location/coordinate";
    public static final String MIME_TYPE_TEXT = "text/plain";
    public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_IMAGE_JPEG_PREVIEW = "image/jpeg+preview";
    public static final String MIME_TYPE_IMAGE_PNG = "image/png";
    public static final String MIME_TYPE_IMAGE_PNG_PREVIEW = "image/png+preview";
    public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    public static final String MIME_TYPE_IMAGE_GIF_PREVIEW = "image/gif+preview";
    public static final String MIME_TYPE_IMAGE_DIMENSIONS = "application/json+imageSize";

    public static final Atlas.DownloadQueue downloadQueue = new DownloadQueue(2);
    public static final String TAG = Atlas.class.getSimpleName();
    public static final boolean debug = false;
    public static final ImageLoader imageLoader = new ImageLoader();

    public static String getInitials(Participant p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getFirstName() != null && p.getFirstName().trim().length() > 0 ? p.getFirstName().trim().charAt(0) : "");
        sb.append(p.getLastName() != null && p.getLastName().trim().length() > 0 ? p.getLastName().trim().charAt(0) : "");
        return sb.toString();
    }

    public static String getFirstNameLastInitial(Participant p) {
        StringBuilder sb = new StringBuilder();
        if (p.getFirstName() != null && p.getFirstName().trim().length() > 0) {
            sb.append(p.getFirstName().trim());
        }
        if (p.getLastName() != null && p.getLastName().trim().length() > 0) {
            sb.append(" ").append(p.getLastName().trim().charAt(0));
            sb.append(".");
        }
        return sb.toString();
    }

    public static String getFullName(Participant p) {
        StringBuilder sb = new StringBuilder();
        if (p.getFirstName() != null && p.getFirstName().trim().length() > 0) {
            sb.append(p.getFirstName().trim());
    }
        if (p.getLastName() != null && p.getLastName().trim().length() > 0) {
            sb.append(" ").append(p.getLastName().trim());
        }
        return sb.toString();
    }

    public static AtlasDrawable imageFromUrl(String url) {
        if (url == null) throw new IllegalArgumentException("url cannot be null");
        AtlasDrawable result = new AtlasDrawable(url);
        downloadQueue.schedule(url, null, result);
        return result;
    }

    public static AtlasDrawable imageFromUrlOrFile(String url, File imageFile) {
        if (url == null) throw new IllegalArgumentException("url cannot be null");
        AtlasDrawable result;
        if (imageFile != null && imageFile.exists() && !imageFile.isDirectory()) {
            result = new AtlasDrawable(url, imageFile);
            if (result.inflateImmediately) {
                result.requestInflate();
            }
        } else {
            result = new AtlasDrawable(url);
            downloadQueue.schedule(url, imageFile, result);
        }
        return result;
    }

    /** @return if Today: time. If Yesterday: "Yesterday", if within one week: day of week, otherwise: dateFormat.format() */
    public static String formatTimeShort(Date dateTime, DateFormat timeFormat, DateFormat dateFormat) {
    
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterMidnight = todayMidnight - Tools.TIME_HOURS_24;
        long weekAgoMidnight = todayMidnight - Tools.TIME_HOURS_24 * 7;
        
        String timeText = null;
        if (dateTime.getTime() > todayMidnight) {
            timeText = timeFormat.format(dateTime.getTime()); 
        } else if (dateTime.getTime() > yesterMidnight) {
            timeText = "Yesterday";
        } else if (dateTime.getTime() > weekAgoMidnight){
            cal.setTime(dateTime);
            timeText = Tools.TIME_WEEKDAYS_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            timeText = dateFormat.format(dateTime);
        }
        return timeText;
    }

    /** Today, Yesterday, Weekday or Weekday + date */
    public static String formatTimeDay(Date sentAt) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long todayMidnight = cal.getTimeInMillis();
        long yesterMidnight = todayMidnight - Tools.TIME_HOURS_24;
        long weekAgoMidnight = todayMidnight - Tools.TIME_HOURS_24 * 7;
        
        String timeBarDayText = null;
        if (sentAt.getTime() > todayMidnight) {
            timeBarDayText = "Today"; 
        } else if (sentAt.getTime() > yesterMidnight) {
            timeBarDayText = "Yesterday";
        } else if (sentAt.getTime() > weekAgoMidnight) {
            cal.setTime(sentAt);
            timeBarDayText = Tools.TIME_WEEKDAYS_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            timeBarDayText = Tools.sdfDayOfWeek.format(sentAt);
        }
        return timeBarDayText;
    }

    /**
     * Participant allows Atlas classes to display information about users, like Message senders,
     * Conversation participants, TypingIndicator users, etc.
     */
    public interface Participant {
        /**
         * Returns the first name of this Participant.
         * 
         * @return The first name of this Participant
         */
        String getFirstName();

        /**
         * Returns the last name of this Participant.
         *
         * @return The last name of this Participant
         */
        String getLastName();
        
        /**
         * Returns drawable to be used as paprticipant's avatar in Atlas Views.
         * If undefined, initials would be used instead.
         * 
         * @return drawable, or null 
         */
        android.graphics.drawable.Drawable getAvatarDrawable();
        
        public static Comparator<Participant> COMPARATOR = new FilteringComparator("");
    }

    /**
     * ParticipantProvider provides Atlas classes with Participant data.
     */
    public interface ParticipantProvider {
        /**
         * Returns a map of all Participants by their unique ID who match the provided `filter`, or
         * all Participants if `filter` is `null`.  If `result` is provided, it is operated on and
         * returned.  If `result` is `null`, a new Map is created and returned.
         *
         * @param filter - <b>null</b> or filter to apply to Participants (generally text from quick-search box)
         * @param result - <b>null</b> map to place results. If null, new instance needs to be created
         * @return result - map of all matching Participants keyed by userId
         *
         * <p>TODO: drop "return value", use not-null "result" map everywhere
         * Why: re-use user-generated Map instance may be helpfull for full-contact list, but it would be
         * modified by user's code during next getParticipants call with filter
         * </p>
         */
        Map<String, Participant> getParticipants(String filter, Map<String, Participant> result);

        /**
         * Returns the Participant with the given ID, or `null` if the participant is not yet
         * available.
         *
         * @return The Participant with the given ID, or `null` if not available.
         */
        Atlas.Participant getParticipant(String userId);
    }

    public static final class FilteringComparator implements Comparator<Atlas.Participant> {
        private final String filter;
    
        /**
         * @param filter - the less indexOf(filter) the less order of participant
         */
        public FilteringComparator(String filter) {
            this.filter = filter;
        }
    
        @Override
        public int compare(Atlas.Participant lhs, Atlas.Participant rhs) {
            int result = subCompareCaseInsensitive(lhs.getFirstName(), rhs.getFirstName());
            if (result != 0) return result;
            return subCompareCaseInsensitive(lhs.getLastName(), rhs.getLastName());
        }
    
        private int subCompareCaseInsensitive(String lhs, String rhs) {
            int left = lhs != null ? lhs.toLowerCase().indexOf(filter) : -1;
            int right = rhs != null ? rhs.toLowerCase().indexOf(filter) : -1;
    
            if (left == -1 && right == -1) return 0;
            if (left != -1 && right == -1) return -1;
            if (left == -1 && right != -1) return 1;
            if (left - right != 0) return left - right;
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    }


    public static final class Tools {
        /** Millis in 24 Hours */
        public static final int TIME_HOURS_24 = 24 * 60 * 60 * 1000;
        // TODO: localization required to all time based constants below
        public static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm a");
        public static final SimpleDateFormat sdfDayOfWeek = new SimpleDateFormat("EEE, LLL dd,");
        /** Ensure you decrease value returned by Calendar.get(Calendar.DAY_OF_WEEK) by 1. Calendar's days starts from 1. */
        public static final String[] TIME_WEEKDAYS_NAMES = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        public static final BitmapDrawable EMPTY_DRAWABLE = new BitmapDrawable(Bitmap.createBitmap(new int[] { Color.TRANSPARENT }, 1, 1, Bitmap.Config.ALPHA_8));

        public static String toString(MotionEvent event) {
            StringBuilder sb = new StringBuilder();

            sb.append("action: ");
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN    : sb.append("DOWN"); break;
                case MotionEvent.ACTION_UP      : sb.append("UP  "); break;
                case MotionEvent.ACTION_MOVE    : sb.append("MOVE"); break;
                case MotionEvent.ACTION_CANCEL  : sb.append("CANCEL"); break;
                case MotionEvent.ACTION_SCROLL  : sb.append("SCROLL"); break;
                case MotionEvent.ACTION_POINTER_UP     : {
                    sb.append("ACTION_POINTER_UP");
                    final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    sb.append(" pointer: ").append(pointerIndex);
                    break;
                }
                case MotionEvent.ACTION_POINTER_DOWN   : {
                    sb.append("ACTION_POINTER_DOWN");
                    final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    sb.append(" pointer: ").append(pointerIndex);
                    break;
                }
                default                         : sb.append(event.getAction()); break;
            }
            sb.append(", pts: [");
            for (int i = 0; i < event.getPointerCount(); i++) {
                sb.append(i > 0 ? ", ":"");
                sb.append(i).append(": ").append(String.format("%.1fx%.1f", event.getX(i), event.getY(i)));
            }
            sb.append("]");
            return sb.toString();
        }

        public static final AdapterOnItem VIEW_GROUP = new AdapterOnItem() {
            public String toString(Object what) {
                View v = (View) what;
                int id = v.getId();
                String fieldName = getField(id, R.id.class);
                Rect vr = new Rect();
                v.getGlobalVisibleRect(vr);
                return "" + fieldName + " / " + v.getClass().getSimpleName()
                    + " " + v.getWidth() + "x" + v.getHeight() + " ." + vr.left + "x" + vr.top
                    + (v.getVisibility() == View.GONE ? " GONE" : "")
                    + (v.getVisibility() == View.INVISIBLE ? " INVISIBLE" : "")
                    + " " + v.hashCode()
                    + (v.getTag() != null ? " " + v.getTag() : "")
                    + " : " + v;
            }
            public Object item(int at, Object from) {
                if (from == null) throw new IllegalStateException("Not implemented");
                if (!(from instanceof ViewGroup)) throw new IllegalArgumentException("from must be ViewGroup");
                ViewGroup viewGroup = (ViewGroup) from;
                return viewGroup.getChildAt(at);
            }
            public int items(Object from) {
                if (from == null) throw new IllegalStateException("Not implemented");
                if (!(from instanceof ViewGroup)) throw new IllegalArgumentException("from must be ViewGroup");
                ViewGroup viewGroup = (ViewGroup) from;
                return viewGroup == null ? 0 : viewGroup.getChildCount();
            }
        };

        public static float[] getRoundRectRadii(float[] cornerRadiusDp, final DisplayMetrics displayMetrics) {
            float[] result = new float[8];
            return getRoundRectRadii(cornerRadiusDp, displayMetrics, result);
        }

        public static float[] getRoundRectRadii(float[] cornerRadiusDp, final DisplayMetrics displayMetrics, float[] result) {
            if (result.length < cornerRadiusDp.length * 2) throw new IllegalArgumentException("result[] is shorter than required. result: " + result.length + ", required: " + cornerRadiusDp.length * 2);
            for (int i = 0; i < cornerRadiusDp.length; i++) {
                result[i * 2] = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDp[i], displayMetrics);
                result[i * 2 + 1] = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDp[i], displayMetrics);
            }
            return result;
        }

        public static float dp2px(float dp, Context context) {
            return dp2px(dp, context.getResources().getDisplayMetrics());
        }

        public static float dp2px(float dp, DisplayMetrics displayMetrics) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics);
        }

        public static float px2dp(float px, Context ctx) {
            return px2dp(px, ctx.getResources().getDisplayMetrics());
        }

        public static float px2dp(float px, DisplayMetrics displayMetrics) {
            double ratio = 1.0 / dp2px(1, displayMetrics);
            return (float) (px * ratio);
        }

        public static boolean isMainThread() {
            return Looper.myLooper() == Looper.getMainLooper();
        }

        public static View findChildById(ViewGroup group, int id) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getId() == id) return child;
            }
            return null;
        }

        public static void closeQuietly(InputStream stream) {
            if (stream == null) return;
            try {
                stream.close();
            } catch (Throwable ignoredQueitly) {}
        }

        public static void closeQuietly(OutputStream stream) {
            if (stream == null) return;
            try {
                stream.close();
            } catch (Throwable ignoredQueitly) {}
        }

        /**
         * @return number of copied bytes
         */
        public static int streamCopyAndClose(InputStream from, OutputStream to) throws IOException {
            int totalBytes = streamCopy(from, to);
            from.close();
            to.close();
            return totalBytes;
        }

        /**
         * @return number of copied bytes
         */
        public static int streamCopy(InputStream from, OutputStream to) throws IOException {
            byte[] buffer = new byte[65536];
            int bytesRead = 0;
            int totalBytes = 0;
            for (; (bytesRead = from.read(buffer)) != -1; totalBytes += bytesRead) {
                to.write(buffer, 0, bytesRead);
            }
            return totalBytes;
        }

        public static String toStringSpec(int measureSpec) {
            switch (View.MeasureSpec.getMode(measureSpec)) {
                case View.MeasureSpec.AT_MOST : return "" + View.MeasureSpec.getSize(measureSpec) + ".A";
                case View.MeasureSpec.EXACTLY : return "" + View.MeasureSpec.getSize(measureSpec) + ".E";
                default                  : return "" + View.MeasureSpec.getSize(measureSpec) + ".U";
            }
        }

        public static String toStringSpec(int widthSpec, int heightSpec) {
            return toStringSpec(widthSpec) + "|" + toStringSpec(heightSpec);
        }

        //---------------------------          ----------------------//
        private static final Handler uiHandler = new Handler(Looper.getMainLooper());
        private static final ExecutorService httpExecutor = Executors.newCachedThreadPool();

        public static Response http(Request req) {
            return http(req.url, req.httpMethod, req.headers, req.body);
        }

        public static void http(Request req, Callback callback) {
            req.callback = callback;
            httpExecutor.execute(req);
        }

        public static class Request implements Runnable {

            /** TODO: state guards */
            public final void run() {
                // no response means .run() first time => execute in background
                if (this.rsp == null) {
                    this.rsp = http(url, httpMethod, headers, body);
                    if (callback != null) {
                        uiHandler.post(this);
                    }
                    // executing callback
                } else if (callback != null) {
                    callback.onComplete(this, rsp, rsp.exception);
                } else {
                    throw new IllegalStateException("No callback found. Request cannot be executed twice: " + rsp);
                }
            }

            public static Request get(String url) {
                return new Request(url, HTTP_GET, null, null);
            }

            public static Request post(String url) {
                return new Request(url, HTTP_POST, null, null);
            }

            public static Request put(String url) {
                return new Request(url, HTTP_PUT, null, null);
            }

            private String url;
            private String httpMethod;
            private String[] headers;
            private byte[] body;

            private Response rsp;
            private Callback callback;


            public Request(String url, String httpMethod, String[] headers, byte[] body) {
                this.url = url;
                this.httpMethod = httpMethod;
                this.headers = headers;
                this.body = body;
            }

            public Request headers(String... headers) {
                checkHeaders(headers);
                this.headers = headers;
                return this;
            }

            public Request body(byte[] body) {
                this.body = body;
                return this;
            }

            public Request body(String body) {
                this.body = body != null ? body.getBytes() : null;
                return this;
            }

            public String toString() {
                return httpMethod + " " + url + " " + Dt.toString(headers);
            }

        }

        public static abstract class Callback {
            public abstract void onComplete(Request req, Response rsp, Exception e);
        }

        /*---------------------------          ----------------------*/
        /*                              HTTP                         */
        /*---------------------------          ----------------------*/

        private static final int CONNECTION_TIMEOUT = 10000;
        private static final int SOCKET_TIMEOUT = CONNECTION_TIMEOUT;
        private static final String TAG = Tools.class.getSimpleName();

        static SSLSocketFactory sslFactory;

        static {
            try {
                SSLContext sslContext = SSLContext.getInstance(InternalSocketFactory.TLS_PROTOCOL);
                sslContext.init(null, new TrustManager[]{getX509TrustManager()}, new SecureRandom());
                sslFactory = new InternalSocketFactory(sslContext);
            } catch (Exception e) {
                Log.e(TAG, "<> failed to create SSLSocketFactory", e);
            }
        }

        public static final String HTTP_GET = "GET";
        public static final String HTTP_POST = "POST";
        public static final String HTTP_PUT = "PUT";

        public static String httpString(String url, String method, String body, String... headers) throws IOException {
            return Dt.toString(http(url, method, headers, body != null ? body.getBytes() : null).bodyInputStream());
        }

        public static Response httpGet(String url, String... headers) {
            return http(url, HTTP_GET, null, headers);
        }

        public static Response httpPost(String url, String body, String... headers) {
            return http(url, HTTP_POST, body, headers);
        }

        /**
         * @param url    - just an URL
         * @param method - "POST" or "GET" or {@link #HTTP_GET} or {@link #HTTP_POST}
         * @param body   - may be null. will be used for POST requests
         * @return input stream with results. Use {@link #toString(InputStream)} to extract basic String result from there
         */
        public static Response http(String url, String method, String body, String... headers) {
            return http(url, method, headers, body != null ? body.getBytes() : null);
        }

        /**
         * <pre>
         * Tomcat testing statusCode:
         *                | body       | status message        | error stream
         * - 0   - 99   -> Timeout,     no status message,      no error stream,
         * - 100        -> exception on responseCode
         * - 101 - 199  -> Timeout,     some (100-102)/null,    no error stream,
         * - 200 - 299  -> Body,        some (200-208)/null     no error stream
         * - 205        -> Timeout,     Reset Content,          no error stream
         * - 300 - 399  -> Body,        some (300-309)/null     no error stream
         * - 400 - 499  -> Exception,   some(400-417)/null      Body
         * - 500 - 599  -> Exception,   some(500-511)/null      Body
         * - 600 - 999  -> Exception,   null                    Body
         * </pre>
         * @return response - response with memory-based storage
         * <p>http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
         */
        public static Response http(String url, String method, String[] headers, byte[] body) {
            boolean debug = false;
            if (debug) Log.i(TAG, "http() " + method + " " + url + " " + (body != null ? body.length + " bytes: " + Dt.encode64(body) : ""));
            checkHeaders(headers);
            URL urlToOpen;
            try {urlToOpen = new URL(url);} catch (MalformedURLException e) {throw new IllegalArgumentException("url is malformed. url: " + url, e);}
            long startedAt = System.currentTimeMillis();
            Response rsp = new Response();
            rsp.url = url;

            // open connection
            HttpURLConnection httpConn = null;
            try {
                httpConn = (HttpURLConnection)  urlToOpen.openConnection();
                if (url.startsWith("https:")) {
                    ((HttpsURLConnection) httpConn).setSSLSocketFactory(sslFactory);
                }
                httpConn.setConnectTimeout(CONNECTION_TIMEOUT);
                httpConn.setReadTimeout(SOCKET_TIMEOUT);
                httpConn.setDoInput(true);
                httpConn.setRequestProperty("Content-Type", "application/octet-stream");
                httpConn.setRequestProperty("Accept",       "application/octet-stream");
                httpConn.setInstanceFollowRedirects(false);
            } catch (Exception e) {
                rsp.exception = e;
                return rsp;
            }

            // send request
            try {
                httpConn.setRequestMethod(method);
                if (headers != null) {
                    for (int i = 0; i < headers.length; i += 2) {
                        String key = headers[i];
                        String value = headers[i + 1];
                        httpConn.setRequestProperty(key, value);
                    }
                }

                if (body != null) {
                    if (!HTTP_POST.equals(method) && !HTTP_PUT.equals(method)) {
                        Log.e(TAG, "http() body cannot be send using " + method);
                    } else {
                        httpConn.setDoOutput(true);
                        OutputStream os = httpConn.getOutputStream();
                        os.write(body);
                        os.close();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "http() failed to send request", e);
                rsp.exception = new IOException("Failed to send request [" + e.getMessage() + "]", e) ;
                return rsp;
            }

            // check results
            try {

                int responseCode = httpConn.getResponseCode();

                rsp.headers = new HashMap<String, String>();
                Map<String, List<String>> connHeaders = httpConn.getHeaderFields();
                for (String key : connHeaders.keySet()) {
                    rsp.headers.put(key, httpConn.getHeaderField(key));
                }
                rsp.code = responseCode;
                rsp.statusMsg = httpConn.getResponseMessage();
                if (debug) Log.d(TAG, "http() code: " + rsp.code + ", message: " + rsp.statusMsg);

                // code > 399 -> .getInputStream() throws IOException "Server returned code 400+"
                //                Go to getErrorStream() instead
                // code < 100 -> TimeoutException will be thrown on reading
                if (responseCode >= 200 && responseCode < 400) {
                    InputStream is = httpConn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
                    streamCopy(is, baos);
                    byte[] bytes = baos.toByteArray();
                    if (debug) Log.i(TAG, "http() " + String.format("%5d", bytes.length) + " bytes received:\n" + Dt.encode64(bytes));
                    rsp.bodyBytes = bytes;
                    is.close();
                } else if (responseCode >= 400) {
                    InputStream es = httpConn.getErrorStream();
                    if (es != null) {
                        String errorDetails = es != null ? Dt.toString(es) : null;
                        if (debug) Log.d(TAG, "http() code: " + responseCode + ", message: " + httpConn.getResponseMessage() + ", e_stream: " + errorDetails + " url: [" + url + "]");
                        rsp.bodyBytes = errorDetails.getBytes();
                        es.close();
                    }
                } /* else code 0 - 199 ignore */

            } catch (IOException ex) {
                InputStream es = httpConn.getErrorStream();
                String errorDetails = es != null ? Dt.toString(es) : "<no error stream>";
                Log.e(TAG, "http() failed to get response. code: " + rsp.code + " e_stream: " + errorDetails + " url: [" + url + "]", ex);
                rsp.exception = new IOException("Failed to get response [" + ex.getMessage() + "]", ex);
                //if (errorDetails != null) rsp.bodyBytes = errorDetails.getBytes();
                try {es.close();}catch(Exception ignored) {};
            } finally {
                long duration = System.currentTimeMillis() - startedAt;
                rsp.duration = duration;
                if (debug) Log.i(TAG, "http() finished in " + duration + "ms" + ", url: [" + url + "]" );
            }
            return rsp;
        }

        private static void checkHeaders(String[] headers) {
            if (headers != null && headers.length % 2 != 0) throw new IllegalArgumentException("headers should have valid key-value pairs. length: " + headers.length);
        }

        /** Convinient class for HTTP responses */
        public static class Response {
            private String url;
            private long duration;
            private Exception exception;
            private int code = -1;
            private String statusMsg;
            private Map<String, String> headers;
            private byte[] bodyBytes;

            public int code() {
                return code;
            }
            public String statusMessage() {
                return getStatusMessage();
            }
            private String getStatusMessage() {
                return statusMsg;
            }
            public String header(String name) {
                return headers == null ? null : headers.get(name);
            }
            public String getHeader(String name) {
                return header(name);
            }
            public boolean isSuccessful() {
                return exception == null && (code >= 200 && code < 300);
            }
            public Exception getException() {
                return exception;
            }
            public boolean isError() {
                return !isSuccessful();
            }
            public InputStream bodyInputStream() {
                if (bodyBytes == null) return null;
                return new ByteArrayInputStream(bodyBytes);
            }
            public InputStream getBodyInputStream() {
                return bodyInputStream();
            }
            public String bodyString() {
                if (bodyBytes == null) return null;
                return new String(bodyBytes);
            }
            public String getBodyString() {
                return bodyString();
            }
            public String url() {
                return url;
            }
            public String toString() {
                String body = null;
                if (bodyBytes != null && bodyBytes.length > 0) {
                    body = bodyBytes.length < 4096 ? new String(bodyBytes) : new String(bodyBytes, 0, 4096);
                }
                return ""
                    + "code: " + code
                    + ", status:  " + statusMsg
                    + "\n" + url + ", took " + duration + "ms"
                    + (exception != null ? "\ne: " + exception + ", ": "")
                    + ", headers: " + Dt.toString(headers, "\n", "\n")
                    + "\n" + (body == null ? "<no body>" : body)
                    ;
            }

        }

        private static X509TrustManager getX509TrustManager() {
            return new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                public X509Certificate[] getAcceptedIssuers() {return null;}
            };
        }

        public static class InternalSocketFactory extends SSLSocketFactory {
            private final static String TLS_PROTOCOL = "TLS";
            private final static String TLSV2_PROTOCOL = "TLSv1.2";

            SSLContext sslContext = SSLContext.getInstance(TLS_PROTOCOL);

            public InternalSocketFactory(SSLContext context) throws NoSuchAlgorithmException {
                this.sslContext = context;
            }

            @Override
            public Socket createSocket() throws IOException {
                return sslContext.getSocketFactory().createSocket();
            }

            @Override
            public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
                SSLSocket s = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
                s.setEnabledProtocols(new String[] { TLSV2_PROTOCOL });
                return s;
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                return sslContext.getSocketFactory().createSocket();
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return sslContext.getSocketFactory().createSocket();
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                return sslContext.getSocketFactory().createSocket();
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                return sslContext.getSocketFactory().createSocket();
            }

            @Override
            public String[] getDefaultCipherSuites() {
                return new String[0];
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return new String[0];
            }
        }

        public static boolean downloadHttpToFile(String url, File file, SSLSocketFactory sslFactory) {
            return downloadHttpToFile(url, file, HTTP_GET, null, sslFactory);
        }

        public static boolean downloadHttpToFile(String url, File file, String method, byte[] body, SSLSocketFactory sslFactory) {
            if (url == null) Log.e(TAG, "downloadHttpToFile() url is null, file: " + file);

            int timeout = 3000;

            HttpURLConnection httpConn = null;
            try {
                httpConn = openHttpConnection(url, method, body, sslFactory, timeout);

                int responseCode = httpConn.getResponseCode();
                if (responseCode >= 300 || responseCode < 200) {
                    Log.e(TAG, "Expected status 200, but got " + responseCode + ", message: " + httpConn.getResponseMessage() + ", url: [" + url + "]");
                    return false;
                }

            } catch (IOException e) {
                Log.e(TAG, "downloadToFile() cannot execute http request, url: [" + url + "]", e);
                return false;
            }

            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Could not create folders, url: [" + url + "] dir: " + dir.getAbsolutePath());
                return false;
            }

            File tempFile = new File(file.getAbsolutePath() + ".download");

            try {
                InputStream inputStream = httpConn.getInputStream();
                streamCopyAndClose(inputStream, new FileOutputStream(tempFile, false));
            } catch (IOException e) {
                if (debug) Log.e(TAG, "downloadToFile() cannot extract content from http response for [" + url + "]", e);
            } finally {
                httpConn.disconnect();
            }

            if (httpConn.getHeaderField("Content-Length") != null && httpConn.getContentLength() != tempFile.length()) {
                Log.e(TAG, "downloadToFile() File size mismatch for [" + url + "] "
                         + " expected: " + httpConn.getContentLength()
                         + " actual: " + tempFile.length()
                         + " path: " + tempFile.getAbsolutePath());
                tempFile.delete();
                return false;
            }

            // last step
            if (tempFile.renameTo(file)) {
                if (debug) Log.w(TAG, "downloadToFile() Successfully downloaded file: " + file.getAbsolutePath());
                return true;
            } else {
                Log.e(TAG, "downloadToFile() Could not rename temp file: " + tempFile.getAbsolutePath() + " to: " + file.getAbsolutePath());
                return false;
            }

        }

        private static final int MAX_REDIRECT_ATTEMPTS = 5;

        private static HttpURLConnection openHttpConnection(String url, String method, byte[] body, SSLSocketFactory sslFactory, int timeout) throws MalformedURLException, IOException, ProtocolException {
            HttpURLConnection httpConn = null;

            for (int attempt = 0; attempt < MAX_REDIRECT_ATTEMPTS; attempt++) {
                URL uRL = new URL(url);

                if (url.startsWith("https://")) {
                    httpConn = (HttpsURLConnection) uRL.openConnection();
                    if (sslFactory != null) {
                        ((HttpsURLConnection) httpConn).setSSLSocketFactory(sslFactory);
                    }
                } else {
                    httpConn = (HttpURLConnection) uRL.openConnection();
                }
                httpConn.setConnectTimeout(timeout);
                httpConn.setReadTimeout(timeout);
                httpConn.setRequestMethod(method);
                httpConn.setDoInput(true);
                // some servers rejects requests without user-agent with status 400
                httpConn.addRequestProperty("User-Agent", "Atlas-Android 1.0");

                if (HTTP_POST.equals(method) && body != null) {
                    httpConn.setDoOutput(true);
                    OutputStream os = httpConn.getOutputStream();
                    os.write(body);
                    os.close();
                }

                int responseCode = httpConn.getResponseCode();
                if (responseCode == 301) {
                    String location = httpConn.getHeaderField("Location");
                    Log.e(TAG, "Redirect [" + location + "]" + " from url: [" + url + "]");
                    Log.w(TAG, "openHttpConnection() follow redirect " + attempt + " to [" + location + "]");
                    url = location;
                    continue;
                }
                break;

            }
            return httpConn;

        }

        /** escape characters of part.id if they are invalid for filePath */
        public static String escapePath(String partId) {
            return partId.replaceAll("[:/\\+]", "_");
        }

        /** draws lines between opposite corners of provided rect */
        public static void drawX(float left, float top, float right, float bottom, Paint paint, Canvas canvas) {
            canvas.drawLine(left, top, right, bottom, paint);
            canvas.drawLine(left, bottom, right, top, paint);
        }
        /** @see #drawX(float, float, float, float, Paint, Canvas)*/
        public static void drawX(Rect rect, Paint paint, Canvas canvas) {
            drawX(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }
        /** @see #drawX(float, float, float, float, Paint, Canvas)*/
        public static void drawX(RectF rect, Paint paint, Canvas canvas) {
            drawX(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }

        public static void drawPlus(float left, float top, float right, float bottom, Paint paint, Canvas canvas) {
            canvas.drawLine(0.5f * (left + right), top, 0.5f * (left + right), bottom, paint);
            canvas.drawLine(left, 0.5f * (top + bottom),  right, 0.5f * (top + bottom), paint);
        }

        public static void drawPlus(Rect rect, Paint paint, Canvas canvas) {
            drawPlus(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }

        public static void drawPlus(RectF rect, Paint paint, Canvas canvas) {
            drawPlus(rect.left, rect.top, rect.right, rect.bottom, paint, canvas);
        }

        public static void drawPlus(float xCenter, float yCenter, Canvas canvas, Paint paint) {
            canvas.drawLine(xCenter, -10000, xCenter, 10000, paint);
            canvas.drawLine(-10000, yCenter, 10000, yCenter, paint);
        }

        public static void drawPlusCircle(float xCenter, float yCenter, float radius, Paint paint, Canvas canvas) {
            drawPlus(xCenter - 1.1f * radius, yCenter - 1.1f * radius, xCenter + 1.1f * radius, yCenter + 1.1f * radius, paint, canvas);
            canvas.drawCircle(xCenter, yCenter, radius, paint);
        }

        public static void drawRect(float left, float top, float width, float height, Paint p, Canvas canvas) {
            canvas.drawRect(left, top, left + width, top + height, p);
        }
        public static void drawRect(float left, float top, float width, float height, Paint p, Paint strokeP, Canvas canvas) {
            drawRect(left, top, width, height, p, canvas);
            drawRect(left, top, width, height, strokeP, canvas);
        }

        /** Window flags for translucency are available on Android 5.0+ */
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;

        /** Changes Status Bar color On Android 5.0+ devices. Do nothing on devices without translucency support */
        public static void setStatusBarColor(Window wnd, int color) {
            try {
                final Method mthd_setStatusBarColor = wnd.getClass().getMethod("setStatusBarColor", int.class);
                if (mthd_setStatusBarColor != null) {
                    mthd_setStatusBarColor.invoke(wnd, color);
                    wnd.addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    wnd.clearFlags(FLAG_TRANSLUCENT_STATUS);
                }
            } catch (Exception ignored) {}
        }

        public static Rect getViewRectOnScreen(View of, Rect to) {
            int[] locationOnScreen = new int[2];
            of.getLocationOnScreen(locationOnScreen);

            Rect result = to;

            if (to == null) result = new Rect();

            result.left = locationOnScreen[0];
            result.top  = locationOnScreen[1];
            result.right = locationOnScreen[0] + of.getWidth();
            result.bottom = locationOnScreen[1] + of.getHeight();

            return result;
        }

        public static Rect getDrawableRectOnScreen(ImageView view) {
            android.graphics.drawable.Drawable d = view.getDrawable();
            if (d == null) return null;

            Rect vRect = getViewRectOnScreen(view, null);
            switch (view.getScaleType()) {
                case CENTER_CROP :
                    float dW = d.getIntrinsicWidth();
                    float dH = d.getIntrinsicHeight();
                    float imgW = view.getWidth();
                    float imgH = view.getHeight();

                    float dRatio = dW / dH;
                    float iRatio = imgW / imgH;

                    float scale;
                    if (dRatio < iRatio) {              // view wider, use width
                        scale = 1.0f * imgW / dW;       //  scale * drawableW -> imgWidth
                    } else {                            // drawable wider, use height
                        scale = 1.0f * imgH / dH;       //  scale * drawableH -> imgHeight
                    }


                    int scaledW = (int) (dW * scale);
                    int scaledH = (int) (dH * scale);

                    Rect dRect = new Rect();
                    dRect.left  = vRect.left  - (scaledW - vRect.width()) / 2;
                    dRect.top   = vRect.top  - (scaledH - vRect.height()) / 2;
                    dRect.right = vRect.right + (scaledW - vRect.width()) / 2;
                    dRect.bottom  = vRect.bottom  + (scaledH - vRect.height()) / 2;

                    return dRect;

            }
            return vRect;
        }

        public static boolean oneOf(int what, int... these) {
            if (these == null) return false;
            if (these.length == 0) return false;
            for (int i = 0; i < these.length; i++) {
                if (what == these[i]) return true;
            }
            return false;
        }

        public static String findField(Object id, Class... classes) {
            if (classes == null) return null;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0 ; i < classes.length ; i++) {
                String fieldName = getField(id, classes[i]);
                if (fieldName != null) {
                    sb.append(sb.length() == 0 ? "" : ", ");
                    sb.append(fieldName);
                }
            }
            sb.append("]");
            return sb.toString();
        }

        public static String getField(Object id, Class clazz) {
            String TAG = "TAG";
            String fieldName = null;
            try {
                Field[] fields = clazz.getFields();
                for (int i = 0 ; i < fields.length; i++) {
                    Object value = fields[i].get(null);
                    if (value != null && value.equals(id)) {
                        fieldName = fields[i].getName();
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getFieldByValue() failed to find field for value: " + id, e);
            }
            return fieldName;
        }

        public static boolean contains(Object[] arr, Object what) {
            if (arr == null || arr.length == 0) return false;
            if (what == null) return false;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] == what) return true;
            }
            return false;
        }

        public static int findChildIndex(View view, ViewGroup viewGroup) {
            int index = -1;
            for (int i = 0 ; i < viewGroup.getChildCount(); i++ ) {
                if (viewGroup.getChildAt(i) == view) {
                    index = i;
                    break;
                }
            }
            return index;
        }

        public static int getViewPosition(ViewGroup container, View view) {
            if (view == null) return -1;
            for (int i = 0 ; i < container.getChildCount(); i++) {
                View v = container.getChildAt(i);
                if (v == view) return i;
            }
            return -1;
        }

        public static class AnimationAdapter implements AnimationListener {
            public void onAnimationStart(Animation animation) {
            }
            public void onAnimationEnd(Animation animation) {
            }
            public void onAnimationRepeat(Animation animation) {
            }
        }

    }

    /**
     * TODO:
     *
     * - track UI frames to execute inflating only at idle times and avoid carousel of images
     *      requested from the same UI frame
     * - maximum retries should be configurable
     * - imageCache should accept any "Downloader" that download something with progress
     *
     */
    public static class ImageLoader {
        public static final String TAG = Atlas.ImageLoader.class.getSimpleName();
        public static final boolean debug = false;

        private static final int BITMAP_DECODE_RETRIES = 10;
        private static final double MEMORY_THRESHOLD = 0.7;

        private volatile boolean shutdownLoader = false;
        private final Thread processingThread;
        private final Object loaderMonitor = new Object();
        private final ArrayList<ImageSpec> queue = new ArrayList<ImageSpec>();
        private ImageSpec inProgress = null;

        /** image_id -> Bitmap | Movie */
        private LinkedHashMap<Object, ImageCacheEntry> cache = new LinkedHashMap<Object, ImageCacheEntry>(40, 1f, true) {
            private static final long serialVersionUID = 1L;
            protected boolean removeEldestEntry(Entry<Object, ImageCacheEntry> eldest) {
                // calculate available memory
                long maxMemory  = Runtime.getRuntime().maxMemory();
                long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                boolean cleaningRequired = 1.0 * usedMemory / maxMemory > MEMORY_THRESHOLD;

                final Object id = eldest.getKey();
                int bytes = 0;
                if (eldest.getValue().bitmapOrMovie instanceof Bitmap) {
                    bytes = ((Bitmap)eldest.getValue().bitmapOrMovie).getByteCount();
                }
                //boolean debug = true;
                if (cleaningRequired) if (debug) Log.w(TAG, "removeEldestEntry()    cleaning, cache: " + cache.size() + ", queue: " + queue.size() + ", " +  bytes + " bytes for: " + id);
                else                  if (debug) Log.w(TAG, "removeEldestEntry() no cleaning, cache: " + cache.size() + ", queue: " + queue.size());

                if (cleaningRequired) {
                    System.gc();
                }
                return cleaningRequired;
            }
        };

        public ImageLoader() {
            // launching thread
            processingThread = new Decoder("AtlasImageLoader");
            processingThread.start();
        }

        private final class Decoder extends Thread {
            public Decoder(String threadName) {
                super(threadName);
            }
            public void run() {
                if (debug) Log.w(TAG, "ImageLoader.run() started");
                while (!shutdownLoader) {

                    ImageSpec spec = null;
                    // search bitmap ready to inflate
                    // wait for queue
                    synchronized (loaderMonitor) {
                        while (!shutdownLoader && (spec = nextSpec()) == null) {
                            try {
                                loaderMonitor.wait();
                            } catch (InterruptedException e) {}
                        }
                        if (shutdownLoader) return;
                    }
                    inProgress = spec;
                    Object bitmapOrMovie = null;
                    if (spec.gif) {
                        InputStream is = spec.inputStreamProvider.getInputStream();
                        Movie mov = Movie.decodeStream(is);
                        if (debug) Log.w(TAG, "decodeImage() decoded GIF " + mov.width() + "x" + mov.height() + ":" + mov.duration() + "ms");
                        Tools.closeQuietly(is);
                        bitmapOrMovie = mov;
                        if (mov != null) {
                            spec.originalHeight = mov.height();
                            spec.originalWidth  = mov.width();
                        }
                    } else {
                        // decode dimensions
                        long started = System.currentTimeMillis();
                        InputStream streamForBounds = spec.inputStreamProvider.getInputStream();
                        if (streamForBounds == null) {
                            Log.e(TAG, "decodeImage() stream is null! Cancelling request. Spec: " + spec.id + ", provider: " + spec.inputStreamProvider.getClass().getSimpleName()); return;
                        }
                        BitmapFactory.Options originalOpts = new BitmapFactory.Options();
                        originalOpts.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(streamForBounds, null, originalOpts);
                        Tools.closeQuietly(streamForBounds);
                        // update spec if width and height are unknown
                        spec.originalWidth = originalOpts.outWidth;
                        spec.originalHeight = originalOpts.outHeight;

                        if (spec.decodeOnly) {
                            spec.decodeOnly = false;
                            fireOnImageLoaded(spec);
                            continue;
                        }

                        // if required dimensions are not defined or bigger than original - use original dimensions
                        int requiredWidth  = spec.requiredWidth  > 0 ? Math.min(spec.requiredWidth,  originalOpts.outWidth)  : originalOpts.outWidth;
                        int requiredHeight = spec.requiredHeight > 0 ? Math.min(spec.requiredHeight, originalOpts.outHeight) : originalOpts.outHeight;
                        int sampleSize = 1;
                        // Use dimension with higher quality to meet both requirements
                        float widthSampleSize  = sampleSize(originalOpts.outWidth,  requiredWidth);
                        float heightSampleSize = sampleSize(originalOpts.outHeight, requiredHeight);
                        sampleSize = (int)Math.min(widthSampleSize, heightSampleSize);

                        if (spec.originalHeight > 2000 || spec.originalWidth > 2000) {} // print warning for debug purposes

                        if (debug) Log.w(TAG, "decodeImage() sampleSize: " + sampleSize + ", original: " + spec.originalWidth + "x" + spec.originalHeight
                                + " required: " + spec.requiredWidth + "x" + spec.requiredHeight);

                        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                        decodeOpts.inSampleSize = sampleSize;
                        Bitmap bmp = null;
                        InputStream streamForBitmap = spec.inputStreamProvider.getInputStream();
                        try {
                            bmp = BitmapFactory.decodeStream(streamForBitmap, null, decodeOpts);
                        } catch (OutOfMemoryError e) {
                            long requiredBytes = 4 * originalOpts.outWidth * originalOpts.outHeight / sampleSize;
                            //boolean debug = true;
                            if (debug) Log.w(TAG, "decodeImage() out of memory, need " + requiredBytes
                                    + " bytes for " + requiredWidth + "x" + requiredHeight
                                    + " orig " + originalOpts.outWidth + "x" + originalOpts.outHeight + " ss: " + sampleSize
                                    + " cache: " + cache.size() + ", queue: " + queue.size() + ", removing eldest."
                                    + " id: " + spec);
                            int bytesClean = removeEldest(requiredBytes);
                            if (debug) Log.w(TAG, "decodeImage()         bytes clean " + bytesClean);
                            System.gc();
                        }
                        Tools.closeQuietly(streamForBitmap);
                        if (bmp != null) {
                            if (debug) Log.d(TAG, "decodeImage() decoded " + bmp.getWidth() + "x" + bmp.getHeight()
                                    + " " + bmp.getByteCount() + " bytes"
                                    + " req: " + spec.requiredWidth + "x" + spec.requiredHeight
                                    + " original: " + originalOpts.outWidth + "x" + originalOpts.outHeight
                                    + " sampleSize: " + sampleSize
                                    + " in " +(System.currentTimeMillis() - started) + "ms from: " + spec.id);
                        } else {
                            if (debug) Log.d(TAG, "decodeImage() not decoded " + " req: " + requiredWidth + "x" + requiredHeight
                                    + " in " +(System.currentTimeMillis() - started) + "ms from: " + spec.id);
                        }
                        bitmapOrMovie = bmp;
                    }

                    // decoded
                    synchronized (loaderMonitor) {
                        if (bitmapOrMovie != null) {
                            ImageCacheEntry imageCore = new ImageCacheEntry(bitmapOrMovie, spec.originalWidth, spec.originalHeight, spec.inputStreamProvider);
                            cache.put(spec.id, imageCore);
                            fireOnImageLoaded(spec);
                        } else if (spec.retries < BITMAP_DECODE_RETRIES) {
                            spec.retries++;
                            queue.add(0, spec);         // schedule retry
                            loaderMonitor.notifyAll();
                        } /*else {
                            forget about this image, never put it back in queue
                        }*/
                        inProgress = null;
                    }

                    if (debug) Log.w(TAG, "decodeImage()   cache: " + cache.size() + ", queue: " + queue.size() + ", id: " + spec.id);
                }
            }
        }

        private void fireOnImageLoaded(ImageSpec spec) {
            for (int i = spec.listeners.size() - 1; i >= 0 ; i--) {
                ImageLoadListener listener = spec.listeners.remove(i);
                listener.onImageLoaded(spec);
            }
        }

        /**
         *
         * Return maximum possible sampleSize to decode bitmap with dimensions >= minRequired
         *
         * <p>
         * Despite {@link BitmapFactory.Options#inSampleSize} documentation, sampleSize
         * handles properly only 2^n values. Other values are handled as nearest lower 2^n.<p>
         * I.e. result bitmap with <br>
         * <code>
         *      opts.sampleSize = 3 is the same as opts.sampleSize = 2<br>
         *      opts.sampleSize = 5 is the same as opts.sampleSize = 4
         * </code>
         *
         * @return bitmap sampleSize values [1, 2, 4, 8, .. 2^n]
         */
        private static int sampleSize(int originalDimension, int minRequiredDimension) {
            int sampleSize = 1;
            while (originalDimension / (sampleSize * 2) > minRequiredDimension) {
                sampleSize *= 2;
                if (sampleSize >= 32) break;
            }
            return sampleSize;
        }

        public Object getImageFromCache(Object id) {
            synchronized (loaderMonitor) {
                ImageCacheEntry imageEntry = cache.get(id);
                if (imageEntry == null) return null;
                return imageEntry.bitmapOrMovie;
            }
        }

        /** @return originalImageWidth if image is in cache, 0 otherwise */
        public int getOriginalImageWidth(Object id) {
            synchronized (loaderMonitor) {
                ImageCacheEntry imageEntry = cache.get(id);
                if (imageEntry == null) return 0;
                return imageEntry.originalWidth;
            }
        }

        /** @return originalImageHeight if image is in cache, 0 otherwise */
        public int getOriginalImageHeight(Object id) {
            synchronized (loaderMonitor) {
                ImageCacheEntry imageEntry = cache.get(id);
                if (imageEntry == null) return 0;
                return imageEntry.originalHeight;
            }
        }

        /**
         * @return - byteCount of removed bitmap if bitmap found. <bold>-1</bold> otherwise
         */
        private int removeEldest() {
            //boolean debug = true;
            synchronized (loaderMonitor) {
                if (cache.size() > 0) {
                    Map.Entry<Object, ImageCacheEntry> entry = cache.entrySet().iterator().next();
                    Object bmp = entry.getValue().bitmapOrMovie;
                    cache.remove(entry.getKey());
                    int releasedBytes = (bmp instanceof Bitmap) ? ((Bitmap) bmp).getByteCount() : 0; /*((Movie)bmp).byteCount(); */
                    if (debug) Log.w(TAG, "removeEldest() id: " + entry.getKey() + ", bytes: " + releasedBytes);
                    return releasedBytes;
                } else {
                    if (debug) Log.w(TAG, "removeEldest() nothing to remove...");
                    return -1;
                }
            }
        }

        private int removeEldest(long bytesToFree) {
            synchronized (loaderMonitor) {
                int totalClean = 0;
                while (cache.size() > 0) {
                    int clean = removeEldest();
                    if (clean < 1) return totalClean;
                    totalClean += clean;
                    if (totalClean > bytesToFree) return totalClean;
                }
                return totalClean;
            }

        }

        /**
         * @see #requestImage(Object, InputStreamProvider, int, int, boolean, ImageLoadListener, boolean)
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, ImageLoader.ImageLoadListener loadListener) {
            return requestImage(id, streamProvider, 0, 0, false, loadListener, false);
        }

        /**
         * @see #requestImage(Object, InputStreamProvider, int, int, boolean, ImageLoadListener, boolean)
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, ImageLoader.ImageLoadListener loadListener, boolean decodeOnly) {
            return requestImage(id, streamProvider, 0, 0, false, loadListener, decodeOnly);
        }

        /**
         * @see #requestImage(Object, InputStreamProvider, int, int, boolean, ImageLoadListener, boolean)
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, boolean gif, ImageLoader.ImageLoadListener loadListener) {
            return requestImage(id, streamProvider, 0, 0, gif, loadListener, false);
        }

        /**
         * Most recently requested images would be inflated first to provide the quickest response
         * (i.e. if user scroll 100 images back and force, the most important one is that he stopped at,
         * and it is the last requested in general scenario)
         *
         * @param id                - something you will use to get image from cache later
         * @param streamProvider    - something that provides raw bytes. See {@link FileStreamProvider}
         * @param requiredWidth     -
         * @param requiredHeight    - provide image dimensions you need to save memory if original dimensions are bigger. 0 means no requirements, inflate as is
         * @param gif               - android.graphics.Movie would be decoded instead of Bitmap.
         * @param loadListener      - something you can use to be notified when image is loaded
         * @param decodeOnly        - will perform decode only and update {@link ImageSpec#originalWidth} and {@link ImageSpec#originalHeight}
         */
        public ImageSpec requestImage(Object id, InputStreamProvider streamProvider, int requiredWidth, int requiredHeight, boolean gif, ImageLoader.ImageLoadListener loadListener, boolean decodeOnly) {
            if (!decodeOnly && (requiredWidth == 0 || requiredHeight == 0)) {
                Log.e(TAG, "requestImage() wtf decode with 0x0 required? " + id + " from: " + Dt.printStackTrace());
            }

            ImageSpec spec = null;
            synchronized (loaderMonitor) {
                for (int i = 0; i < queue.size(); i++) {        // remove from deep deep blue
                    if (queue.get(i).id.equals(id)) {
                        spec = queue.remove(i);
                        if (debug) Log.w(TAG, "requestImage() found scheduled: " + spec + ", from: " + Dt.printStackTrace());
                        break;
                    }
                }
                if (spec == null) {
                    spec = new ImageSpec();
                    spec.id = id;
                    spec.inputStreamProvider = streamProvider;
                    spec.requiredHeight = requiredHeight;
                    spec.requiredWidth = requiredWidth;
                    spec.listeners.add(loadListener);
                    spec.gif = gif;
                    spec.decodeOnly = decodeOnly;
                } else {
                    spec.listeners.add(loadListener);
                    if (decodeOnly == false) spec.decodeOnly = false;  // now decode is not enough
                }
                // check something we have in memory for such id
                ImageCacheEntry imageEntry = cache.get(id);
                if (imageEntry != null && imageEntry.inputStreamProvider.equals(streamProvider)) {
                    if (debug) Log.w(TAG, "requestImage() wow, we already inflated one: [" + imageEntry.originalWidth + "x" + imageEntry.originalHeight + "] put it in spec");
                    spec.originalWidth = imageEntry.originalWidth;
                    spec.originalHeight = imageEntry.originalHeight;
                }

                queue.add(0, spec);                             // and put it to the surface in front of all
                loaderMonitor.notifyAll();
            }
            if (debug) Log.w(TAG, "requestBitmap() cache: " + cache.size() + ", queue: " + queue.size() + ", id: " + id + ", reqs: " + requiredWidth + "x" + requiredHeight);
            return spec;
        }

        /** pick first spec in queue that has inputstream ready */
        private ImageSpec nextSpec() {
            synchronized (loaderMonitor) {
                // picking from queue
                for (int i = 0; i < queue.size(); i++) {
                    ImageSpec imageSpec = queue.get(i);
                    if (imageSpec.inputStreamProvider.ready()) { // ready to inflate
                        return queue.remove(i);
                    }
                }
                return null;
            }
        }

        /** @return imageSpec if image is scheduled, null otherwise */
        public ImageSpec getScheduled(Object id) {
            synchronized (loaderMonitor) {
                if (inProgress != null && inProgress.id.equals(id)) return inProgress;
                // picking from queue
                for (int i = 0; i < queue.size(); i++) {
                    ImageSpec imageSpec = queue.get(i);
                    if (imageSpec.id.equals(id)) { // ready to inflate
                        return imageSpec;
                    }
                }
                return null;
            }
        }

        /**
         * a) contains link to actual image, and is used as cache entry<br>
         * b) contains image's original dimensions. Allows to return filled ImageSpec before it is inflated
         */
        private static class ImageCacheEntry {
            public final int originalWidth;
            public final int originalHeight;
            public final InputStreamProvider inputStreamProvider;
            public final Object bitmapOrMovie;

            public ImageCacheEntry(Object bitmapOrMovie, int originalWidth, int originalHeight, InputStreamProvider inputStreamProvider) {
                this.originalWidth = originalWidth;
                this.originalHeight = originalHeight;
                this.inputStreamProvider = inputStreamProvider;
                this.bitmapOrMovie = bitmapOrMovie;
            }
        }

        /**
         * Everything you need to know about image even if it is not in memory
         */
        public static class ImageSpec {
            public Object id;
            public InputStreamProvider inputStreamProvider;
            public int requiredWidth;
            public int requiredHeight;
            public int originalWidth;
            public int originalHeight;
            public boolean gif;
            public int retries = 0;
            public boolean decodeOnly;
            public final ArrayList<ImageLoadListener> listeners = new ArrayList<ImageLoadListener>();

            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(" .o[").append(originalWidth).append("x").append(originalHeight).append("]");
                if (requiredWidth != 0 || requiredHeight != 0) sb.append(", .r[").append(requiredWidth).append("x").append(requiredHeight).append("]");
                sb.append(" .id: ").append(id);
                sb.append(gif ? ", gif" : "");
                return sb.toString();
            }

        }

        public interface ImageLoadListener {
            public void onImageLoaded(ImageSpec spec);
        }

        public static abstract class InputStreamProvider {
            public abstract InputStream getInputStream();
            public abstract boolean ready();
        }
    }

    public static class DownloadQueue {
        private static final String TAG = DownloadQueue.class.getSimpleName();

        private final Object queueMonitor = new Object();
        private final LinkedList<Entry> queue = new LinkedList<Atlas.DownloadQueue.Entry>();
        private final HashMap<String, Entry> scheduledEntries = new HashMap<String, Atlas.DownloadQueue.Entry>();
        private final HashMap<String, Entry> inProgress = new HashMap<String, Atlas.DownloadQueue.Entry>();
        private Thread[] workers;

        private SSLSocketFactory sslSocketFactory = null;

        public DownloadQueue() {
            this(1);
        }
        public DownloadQueue(int workers) {
            this.workers = new Thread[workers];
            for (int i = 0; i < workers; i++) {
                Thread workingThread = new Thread(new Worker());
                workingThread.setDaemon(true);
                workingThread.setName(i == 0 ? "Atlas-HttpLoader" : ("Atlas-HttpLoader-" + i));
                workingThread.start();
            }
        }

        /**
         * {@link #schedule(String, File, CompleteListener)} with <code>File == null</code>
         */
        public void schedule(String url, CompleteListener onComplete) {
            schedule(url, null, onComplete);
        }

        /**
         * {@link #schedule(String, File, CompleteListener)} with <code>first == false</code>
         */
        public void schedule(String url, File toFile, CompleteListener onComplete) {
            schedule(url, toFile, onComplete, false);
        }

        /**
         * Schedule download of content from specified url to file
         *
         * @param toFile - if <b>null</b> queue will create temp file and pass it to {@link CompleteListener#onDownloadComplete(String, File)}
         * @param first  - add in the beginning of the queue
         */
        public void schedule(String url, File toFile, CompleteListener onComplete, boolean first) {
            if (debug) Log.d(TAG, "schedule() url: " + url + " toFile: " + toFile + " onComplete: " + onComplete);
            if (url == null || url.isEmpty()) throw new IllegalArgumentException("url must be defined: [" + url + "], file: " + toFile + ", onComplete: " + onComplete);

            // if url and destination file both are similar to something scheduled - just attach another listener
            // otherwise schedule to download
            synchronized (queueMonitor) {
                Entry scheduled = scheduled(url);
                if (scheduled != null && isSame(scheduled.file, toFile)) {
                    if (onComplete != null) {
                        scheduled.completeListeners.add(onComplete);
                    }
                    if (first && inProgress(url) == null && queue.getFirst() != scheduled) {
                        queue.remove(scheduled);
                        queue.addFirst(scheduled);
                        queueMonitor.notifyAll();
                    }
                } else {
                    Entry toSchedule = new Entry(url, toFile, onComplete);
                    if (first) {
                        queue.addFirst(toSchedule);
                    } else {
                        queue.add(toSchedule);
                    }
                    scheduledEntries.put(toSchedule.url, toSchedule);
                    queueMonitor.notifyAll();
                }
            }
        }

        /**
         * Picks first available entry from queue and fetch it.
         * In progress entries could be found in {@link #inProgress} set
         */
        private final class Worker implements Runnable {
            public void run() {
                while (true) {
                    Entry next = null;
                    synchronized (queueMonitor) {
                        while (queue.size() == 0) {
                            try {
                                queueMonitor.wait();
                            } catch (InterruptedException ignored) {}
                        }
                        next = queue.removeFirst();
                        scheduledEntries.remove(next.url);
                        // onStart
                        inProgress.put(next.url, next);
                    }
                    try {
                        File downloadTo = next.file;
                        if (downloadTo == null) {
                            downloadTo = File.createTempFile(String.valueOf(System.currentTimeMillis()), ".tmp");
                            next.file = downloadTo;
                        }

                        boolean downloaded = Tools.downloadHttpToFile(next.url, downloadTo, sslSocketFactory);

                        if (downloaded) {
                            for (CompleteListener onComplete : next.completeListeners) {
                                onComplete.onDownloadComplete(next.url, downloadTo);
                            }
                        };
                    } catch (Throwable e) {
                        Log.e(TAG, "onComplete() thrown an exception for: " + next.url, e);
                    }
                    // onComplete
                    synchronized (queue) {
                        inProgress.remove(next.url);
                    }
                }
            }
        }

        /** @return true if inProgress or scheduled */
        private Entry scheduled(String url) {
            synchronized (queueMonitor) {
                Entry entry = scheduledEntries.get(url);
                if (entry != null) return entry;
                entry = inProgress.get(url);
                return entry;
            }
        }

        /** @return true if inProgress or scheduled */
        private Entry inProgress(String url) {
            synchronized (queueMonitor) {
                Entry entry = inProgress.get(url);
                return entry;
            }
        }

        public void setSSLSocketFactory(SSLSocketFactory sslFactory) {
            synchronized (queueMonitor) {
                this.sslSocketFactory = sslFactory;
            }
        }

        private static boolean isSame(Object left, Object right) {
            if (left == right) return true;
            if (left == null && right == null) return true;
            if (left != null && right != null && left.equals(right)) return true;
            return false;
        }

        private static class Entry {
            String url;
            File file;
            ArrayList<CompleteListener> completeListeners = new ArrayList<Atlas.DownloadQueue.CompleteListener>(3);
            /** @param file - if null DownloadQueue will create tempFile using {@link File#createTempFile(String, String)} }*/
            public Entry(String url, File file, CompleteListener listener) {
                if (url == null) throw new IllegalArgumentException("url cannot be null");
                this.url = url;
                this.file = file;
                this.completeListeners.add(listener);
            }
        }

        public interface CompleteListener {
            public void onDownloadComplete(String url, File file);
        }
    }

    public static class FileStreamProvider extends ImageLoader.InputStreamProvider {
        final File file;
        public FileStreamProvider(File file) {
            if (file == null) throw new IllegalStateException("File cannot be null");
            if (!file.exists()) throw new IllegalStateException("File must exist!");
            this.file = file;
        }
        public InputStream getInputStream() {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                Log.e(ImageLoader.TAG, "FileStreamProvider.getStream() cannot open file. file: " + file, e);
                return null;
            }
        }
        public boolean ready() {
            if (ImageLoader.debug) Log.w(ImageLoader.TAG, "ready() FileStreamProvider, file ready: " + file.getAbsolutePath());
            return true;
        }
        public boolean equals(Object o) {
            return file.equals(o);
        }
        public int hashCode() {
            return file.hashCode();
        }
    }

    public interface Controller {
        public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState);
    }

}
