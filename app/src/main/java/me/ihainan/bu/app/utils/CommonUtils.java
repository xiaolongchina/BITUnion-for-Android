package me.ihainan.bu.app.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.ihainan.bu.app.R;
import me.ihainan.bu.app.models.Member;
import me.ihainan.bu.app.ui.ProfileActivity;
import me.ihainan.bu.app.utils.network.BUApi;
import okhttp3.Cache;
import ws.vinta.pangu.Pangu;

/**
 * 通用工具类
 */
public class CommonUtils {
    private final static String TAG = CommonUtils.class.getSimpleName();

    /**
     * 显示一个弹窗（dialog）
     *
     * @param context 上下文
     * @param title   弹窗标题
     * @param message 弹窗信息
     */
    public static void showDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        if (!((Activity) context).isFinishing()) {
            builder.show();
        }
    }

    /**
     * 输出 Debug 用途的 Toast 信息
     *
     * @param context 上下文
     * @param message 输出信息
     */
    public static void debugToast(Context context, String message) {
        if (BUApplication.debugMode)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    /**
     * 判断 Context 对应的 Activity 是否仍处于运行状态
     *
     * @param ctx Context 对象
     * @return 返回 True 说明仍在运行，否则已经停止
     */
    public static boolean isRunning(Context ctx) {
        ActivityManager activityManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (ActivityManager.RunningTaskInfo task : tasks) {
            if (ctx.getPackageName().equalsIgnoreCase(task.baseActivity.getPackageName()))
                return true;
        }

        return false;
    }

    /**
     * 设置用户头像点击事件，自动跳转到用户的个人页面
     *
     * @param context  上下文
     * @param view     被点击的 View
     * @param userId   用户 ID。若 username != null 则无视 userId
     * @param userName 用户名
     */
    public static void setUserAvatarClickListener(final Context context, View view, final long userId, final String userName) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, ProfileActivity.class);
                intent.putExtra(ProfileActivity.USER_ID_TAG, userId);
                intent.putExtra(ProfileActivity.USER_NAME_TAG, userName);
                context.startActivity(intent);
            }
        });
    }

    public static void setAvatarImageView(final Context context, final ImageView imageView, final String imageSrc, final int errorImageId) {
        if (BUApplication.badImages.get(imageSrc) != null) {
            Log.d(TAG, "图片在黑名单中 " + imageSrc);
            Picasso.with(context)
                    .load(R.drawable.default_avatar)
                    .into(imageView);
        } else {
            setImageView(context, imageView, imageSrc, errorImageId);
        }
    }

    /**
     * 加载网络图片到指定 ImageView 中，如果缓存中已经存在图片，则不会重新下载
     *
     * @param context      上下文
     * @param imageView    需要显示图片的 ImageView
     * @param imageSrc     图片地址
     * @param errorImageId 发现加载错误后显示的占位图片
     */
    public static void setImageView(final Context context, final ImageView imageView, final String imageSrc, final int errorImageId) {
        // 测试 Offline 模式是否能够正确加载图片
        Picasso.with(context)
                .load(imageSrc)
                .placeholder(R.drawable.empty_avatar)
                .error(errorImageId)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        // 成功，那么啥也不用做
                        Log.d(TAG, "setImageView >> 图片 " + imageSrc + " 已在缓存之中");
                    }

                    @Override
                    public void onError() {
                        if (!CommonUtils.isWifi(context) && BUApplication.saveDataMode) {
                            // TODO: 以更友好的方式显示默认头像
                            // 节省流量模式，不要下载图片
                            Log.d(TAG, "setImageView >> 节省流量模式且非 Wi-Fi 环境，不下载图片 " + imageSrc);
                            Picasso.with(context)
                                    .load(R.drawable.default_avatar)
                                    .error(errorImageId)
                                    .into(imageView);
                        } else {
                            // 非节省流量模式，下载并缓存图片
                            Log.d(TAG, "setImageView >> 非节省流量模式或者 Wi-Fi 环境，正常下载图片 " + imageSrc);
                            Picasso.with(context)
                                    .load(imageSrc)
                                    .placeholder(R.drawable.empty_avatar)
                                    .error(errorImageId)
                                    .into(imageView, new Callback() {
                                        @Override
                                        public void onSuccess() {

                                        }

                                        @Override
                                        public void onError() {
                                            // 放入黑名单
                                            Log.d(TAG, "图片加入到黑名单中 " + imageSrc);
                                            BUApplication.badImages.put(imageSrc, true);
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * 获取用户信息之后的 Callback 类，包含 doSomethingIfHasCached 和 doSomethingIfHasNotCached 两个成员函数
     */
    public static abstract class UserInfoAndFillAvatarCallback {
        public abstract void doSomethingIfHasCached(Member member);

        // 有需要的话进行 Overwrite
        public void doSomethingIfHasNotCached(Member member) {
            doSomethingIfHasCached(member);
        }
    }

    /**
     * 从磁盘中删除图片对应的内存和磁盘缓存
     *
     * @param context 上下文
     * @param imgUrl  图片的 URL
     * @throws IOException 获取磁盘缓存中的 URL 失败
     */
    public static void removeImageFromCache(Context context, String imgUrl) throws IOException {
        Picasso.with(context).invalidate(imgUrl);
        Cache picassoDiskCache = BUApplication.getPicassoCache();
        if (picassoDiskCache != null) {
            Iterator<String> iterator = picassoDiskCache.urls();
            while (iterator.hasNext()) {
                String url = iterator.next();
                if (imgUrl.equals(url)) {
                    Log.d(TAG, "找到图片缓存，准备删除: " + imgUrl);
                    iterator.remove();
                    Log.d(TAG, "删除图片缓存成功: " + imgUrl);
                    break;
                }
            }
        }
    }

    /**
     * 获取用户信息并执行特定操作，如果用户信息已经被缓存，则直接从缓存中获取，否则从服务器拉取并进行缓存
     *
     * @param context  上下文
     * @param userName 用户名
     * @param callback 包含回调函数
     */
    public static void getAndCacheUserInfo(final Context context, String userName,
                                           final UserInfoAndFillAvatarCallback callback) {

        userName = CommonUtils.decode(userName);

        // 从缓存中获取用户信息
        final Member member = (Member) BUApplication.getCache(context)
                .getAsObject(BUApplication.CACHE_USER_INFO + userName);

        if (member != null) {
            Log.i(TAG, "从缓存 " + BUApplication.CACHE_USER_INFO + userName + " 中获取用户 " + userName + " 的缓存数据");

            // Do something HERE!!!
            callback.doSomethingIfHasCached(member);
        } else {
            Log.i(TAG, "准备拉取用户 " + userName + " 的缓存数据");

            // 从服务器拉取数据并写入到缓存当中
            final String finalUserName = userName;
            BUApi.getUserInfo(context, -1,
                    userName,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            // if (((Activity) context).isFinishing()) return;
                            try {
                                if (BUApi.checkStatus(response)) {
                                    Member newMember = BUApi.MAPPER.readValue(
                                            response.getJSONObject("memberinfo").toString(),
                                            Member.class);

                                    // Do something HERE!!!
                                    callback.doSomethingIfHasNotCached(newMember);

                                    // 将用户信息放入到缓存当中
                                    Log.i(TAG, "拉取得到用户 " + finalUserName + " 的数据，放入缓存 " + BUApplication.CACHE_USER_INFO + finalUserName + " 中：" + newMember);
                                    BUApplication.getCache(context).put(
                                            BUApplication.CACHE_USER_INFO + finalUserName,
                                            newMember,
                                            BUApplication.USER_INFO_CACHE_DAYS * ACache.TIME_DAY);
                                } else if ("member_nonexistence".equals(response.getString("msg"))) {
                                    String message = context.getString(R.string.error_user_not_exists) + ": " + CommonUtils.decode(finalUserName);
                                    String debugMessage = message + " - " + response;
                                    Log.w(TAG, debugMessage);
                                    CommonUtils.debugToast(context, debugMessage);
                                } else {
                                    String message = context.getString(R.string.error_unknown_msg) + ": " + response.getString("msg");
                                    String debugMessage = message + " - " + response;
                                    Log.w(TAG, debugMessage);
                                    CommonUtils.debugToast(context, debugMessage);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, context.getString(R.string.error_parse_json) + "\n" + response, e);
                            }
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            if (context instanceof Activity && ((Activity) context).isFinishing())
                                return;
                            String message = context.getString(R.string.error_network);
                            String debugMessage = "getAndCacheUserInfo >> " + message;
                            CommonUtils.debugToast(context, debugMessage);
                            Log.e(TAG, debugMessage, error);
                        }
                    });
        }
    }

    /**
     * 获取图片的真实 URL
     *
     * @param originalURL 原始的 URL
     * @return 图片的真实 URL
     */
    public static String getRealImageURL(String originalURL) {
        String baseUrl = BUApi.OUT_SCHOOL_BASE_URL;

        // URL 解码
        originalURL = CommonUtils.decode(originalURL);
        if (originalURL.endsWith("noavatar.gif"))
            return "file:///android_asset/avatar/default_avatar.jpg";

        String ori = originalURL;

        if (originalURL.startsWith("file:///android_asset")) {
            Log.d(TAG, "getRealImageURL >> " + ori + " - " + ori);
            return ori;
        }

        originalURL = originalURL.replaceAll("^images/", baseUrl + "images/");
        originalURL = originalURL.replaceAll("^../images", baseUrl + "images/");

        // 回帖头像
        if (ori.startsWith("<embed src=") || ori.startsWith("<img src=")) {
            originalURL = originalURL.split("\"")[1];
        }

        // 空地址
        if (originalURL == null || originalURL.equals("")) {
            return "file:///android_asset/avatar/default_avatar.jpg";
        }

        // 完整地址和不完整地址¡¡
        if (originalURL.startsWith("http"))
            originalURL = BUApplication.isInSchool() ? originalURL : originalURL.replace("www", "out");
        else originalURL = baseUrl + originalURL;

        originalURL = originalURL.replaceAll("(http://)?(www|v6|kiss|out).bitunion.org/", baseUrl);
        originalURL = originalURL.replaceAll("http://bitunion.org/", baseUrl);

        // 图片
        originalURL = originalURL.replaceAll("^images/", baseUrl + "images/");

        // 特殊情况
        if (originalURL.endsWith(",120,120")) originalURL = originalURL.replace(",120,120", "");

        if (originalURL.contains("aid="))
            originalURL = "file:///android_asset/avatar/default_avatar.jpg";
        Log.d(TAG, "getRealImageURL >> " + ori + " - " + originalURL);
        return originalURL;
    }

    /**
     * 将 URL 编码字符转换成特定编码类型
     *
     * @param originalStr 原始字符串
     * @param encode      编码类型
     * @return 转换后得到的 utf-8 编码字符串
     */
    public static String decode(String originalStr, String encode) {
        try {
            return URLDecoder.decode(originalStr, encode);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unsupported Encoding", e);
            return originalStr;
        }
    }

    /**
     * 将 URL 编码字符转换成 utf-8 编码
     *
     * @param originalStr 原始字符串
     * @return 转换后得到的 utf-8 编码字符串
     */
    public static String decode(String originalStr) {
        try {
            return URLDecoder.decode(originalStr, "utf-8");
        } catch (Exception e) {
            Log.e(TAG, "Unsupported Encoding", e);
            return originalStr;
        }
    }

    /**
     * 将字符串转换成 URL 编码
     *
     * @param originalStr 原始字符串
     * @return 转换后得到的 URL 编码字符串
     */
    public static String encode(String originalStr) {
        try {
            return URLEncoder.encode(originalStr, "utf-8");
        } catch (Exception e) {
            Log.e(TAG, "Unsupported Encoding", e);
            return originalStr;
        }
    }


    /**
     * 将字符串转换成 URL 编码
     *
     * @param originalStr 原始字符串
     * @return 转换后得到的 URL 编码字符串
     */
    public static String encode(String originalStr, String code) {
        try {
            return URLEncoder.encode(originalStr, code);
        } catch (Exception e) {
            Log.e(TAG, "Unsupported Encoding", e);
            return originalStr;
        }
    }

    /**
     * 格式化日期为标准格式（yyyy-MM-dd hh:mm"）
     *
     * @param date 需要格式化的日期
     * @return 格式化后的日期字符串
     */
    public static String formatDateTime(Date date) {
        return (new SimpleDateFormat("yyyy-MM-dd HH:mm")).format(date);
    }

    /**
     * 格式化日期字符串为标准格式（yyyy-MM-dd hh:mm"）
     *
     * @param dateStr 需要格式化的日期字符串
     * @return 格式化后的日期字符串
     */
    public static String formatDateTime(String dateStr) {
        return (new SimpleDateFormat("yyyy-MM-dd HH:mm")).format(parseDateString(dateStr));
    }

    /**
     * 格式化日期为标准时间格式（hh:mm"）
     *
     * @param date 需要格式化的日期
     * @return 格式化后的时间字符串
     */
    public static String formatTime(Date date) {
        return (new SimpleDateFormat("HH:mm")).format(date);
    }

    /**
     * 格式化日期为标准格式（yyyy-MM-dd"）
     *
     * @param date 需要格式化的日期
     * @return 格式化后的日期字符串
     */
    public static String formatDateTimeToDay(Date date) {
        return (new SimpleDateFormat("yyyy-MM-dd")).format(date);
    }

    /**
     * 判断一个帖子是否是热门帖
     *
     * @param postDate      帖子发表时间
     * @param lastReplyDate 帖子最后回复时间
     * @param replies       总回帖数
     * @return 是否是热门帖子
     */
    public boolean isHotThread(Date postDate, Date lastReplyDate, int replies) {
        int days = (int) (lastReplyDate.getTime() - postDate.getTime()) / (1000 * 60 * 60 * 24);
        return (replies / days >= 5);
    }

    /**
     * 字符串转换为日期
     *
     * @param dateStr 原始字符串
     * @return 转换得到的日期
     */
    public static Date parseDateString(String dateStr) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            return format.parse(dateStr);
        } catch (ParseException e) {
            Log.e(TAG, "错误的日期字符串 " + dateStr, e);
            return new Date();
        }
    }

    /**
     * 获取指定时间距离当前时间的相对距离（如 3 分钟前）
     *
     * @param date 需要比较的时间
     * @return 相对距离字符串
     */
    public static String getRelativeTimeSpanString(Date date) {
        long now = System.currentTimeMillis();
        return DateUtils.getRelativeTimeSpanString(date.getTime(), now, DateUtils.MINUTE_IN_MILLIS).toString();
    }

    /**
     * Unix 时间戳转换为 Date 类型
     *
     * @param timeStamp Unix 时间戳
     * @return Date 类型
     */
    public static Date unixTimeStampToDate(long timeStamp) {
        return new java.util.Date(timeStamp * 1000);
    }

    public static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 截断字符串
     *
     * @param str    原始字符串
     * @param length 截断字符串的最大长度
     * @return 截断后的字符串
     */
    public static String truncateString(String str, int length) {
        if (str == null) return "";
        if (str.length() > length - 3) str = str.substring(0, length - 3) + "...";
        return str;
    }

    /**
     * 获取当前设备名称
     *
     * @return 当前设备名称
     */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String deviceName;
        if (model.startsWith(manufacturer)) {
            deviceName = capitalize(model);
        } else {
            deviceName = capitalize(manufacturer) + " " + model;
        }
        if (realDeviceName.get(deviceName) == null)
            return deviceName;
        else return realDeviceName.get(deviceName);
    }

    /**
     * 厂商代码 -> 具体设备型号哈希表
     */
    private static final Map<String, String> realDeviceName = new HashMap<>();

    static {
        // realDeviceName.put("Sony E6683", "Sony Xperia Z5 Dual");
        // TODO: 添加其他设备信息
    }

    /**
     * 字符串首字母大写
     *
     * @param str 原始字符串
     * @return 首字母大写字符串
     */
    private static String capitalize(String str) {
        if (str == null || str.length() == 0) return "";

        char first = str.charAt(0);
        if (Character.isUpperCase(first)) {
            return str;
        } else {
            return Character.toUpperCase(first) + str.substring(1);
        }
    }

    /**
     * 判断当前是否处于 Wi-Fi 环境
     *
     * @param context 上下文
     * @return <code>true</code> 表示 Wi-Fi 环境，否则不是
     */
    public static boolean isWifi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    /**
     * 获取特定大小字体的高度
     *
     * @param context  上下文
     * @param fontSize 字体大小
     * @return 对应的高度
     */
    public static int getFontHeight(Context context, float fontSize) {
        // Convert Dp To Px
        float px = context.getResources().getDisplayMetrics().density * fontSize + 0.5f;

        // Use Paint to get font height
        Paint p = new Paint();
        p.setTextSize(px);
        Paint.FontMetrics fm = p.getFontMetrics();
        return (int) Math.ceil(fm.descent - fm.ascent);
    }

    /**
     * 获取屏幕大小
     *
     * @param display 屏幕
     * @return 屏幕大小
     */
    public static Point getDisplaySize(Display display) {
        Point size = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            display.getSize(size);
        } else {
            int width = display.getWidth();
            int height = display.getHeight();
            size = new Point(width, height);
        }

        return size;
    }

    /**
     * 以更友好的方式显示文件大小
     *
     * @param size 文件大小，单位为 KB
     * @return 表示文件大小的字符串
     */
    public static String readableFileSize(long size) {
        if (size <= 0) return "0 KB";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * 更新图库
     *
     * @param context 上下文
     * @param file    需要添加至图库的图片
     */
    public static void updateGallery(Context context, File file) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    /**
     * 浏览器打开 URL
     *
     * @param context 上下文
     * @param url     需要打开的 URL
     */
    public static void openBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }

    /**
     * 创建文件用于存储下载图片
     *
     * @return 用于存储图片的文件实例
     */
    public static File getOutputMediaFile(Context context) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mediaStorageDir = context.getExternalFilesDirs(null)[0];
        } else {
            mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                    + "/Android/data/"
                    + context.getApplicationContext().getPackageName()
                    + "/Files");
        }

        return getOutputFile(mediaStorageDir, null);
    }

    /**
     * 创建文件用于存储临时图片
     *
     * @return 用于存储图片的文件实例
     */
    public static File getTmpMediaFile(Context context) {
        // 创建空白目录
        String NOMEDIA = ".nomedia";
        File noMediaFile = new File(context.getExternalCacheDir().getAbsolutePath() + File.separator + NOMEDIA);
        if (!noMediaFile.exists()) {
            try {
                noMediaFile.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "无法创建空白文件", e);
            }
        }

        // 创建临时文件
        File outputDir = context.getExternalCacheDir();
        return getOutputFile(outputDir, null);
    }

    /**
     * 清空临时目录
     *
     * @param context 程序上下文
     */
    public static void deleteTmpDir(Context context) {
        // 创建临时文件
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir.exists()) {
            if (cacheDir.isDirectory())
                for (File child : cacheDir.listFiles())
                    child.delete();
            cacheDir.delete();
        }
    }

    /**
     * 创建指定目录下的输出文件，如果 filename 为空，则根据时间戳取一个名字
     *
     * @param fileDir  指定文件目录
     * @param filename 文件名
     * @return 创建的新文件
     */
    public static File getOutputFile(File fileDir, String filename) {
        // Create the storage directory if it does not exist
        if (!fileDir.exists()) {
            if (!fileDir.mkdirs()) {
                return null;
            }
        }

        // Create a media file name
        File mediaFile;
        if (filename == null) {
            String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
            String mImageName = "BU_" + timeStamp + ".jpg";
            mediaFile = new File(fileDir.getPath() + File.separator + mImageName);
        } else {
            mediaFile = new File(fileDir.getPath() + File.separator + filename);
            if (mediaFile.exists()) {
                mediaFile.delete();
            }
        }
        return mediaFile;
    }

    /**
     * 保存图片到缓存目录下
     *
     * @param context 上下文
     * @param bitmap  需要保存的图片
     * @return 新图片的 Uri
     */
    public static Uri saveImageToTmpPath(Context context, Bitmap bitmap) {
        File pictureFile = getTmpMediaFile(context);

        // 压缩并写入到文件中
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(pictureFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
            return getImageContentUri(context, pictureFile);
        } catch (Exception e) {
            String message = "saveImageToTmpPath >> 保存图片到缓存目录下失败";
            Log.e(TAG, message, e);
            CommonUtils.debugToast(context, message);
            return null;
        }
    }

    /**
     * 压缩图片到指定大小以内用于附件上传
     *
     * @param context     上下文
     * @param oriImageUri 原始图片 Uri
     * @param fileSize    最大文件大小
     * @return 新得到的图片的 Uri
     */
    public static Uri compressImage(Context context, Uri oriImageUri, int fileSize) {
        try {
            // 压缩图片到指定大小
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), oriImageUri);

            double targetWidth = Math.sqrt(fileSize * 1000);
            if (bitmap.getWidth() > targetWidth || bitmap.getHeight() > targetWidth) {
                // 创建操作图片用的 matrix 对象
                Matrix matrix = new Matrix();

                // 计算宽高缩放率
                double x = Math.min(targetWidth / bitmap.getWidth(), targetWidth
                        / bitmap.getHeight());

                // 缩放图片动作
                matrix.postScale((float) x, (float) x);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
            }

            return saveImageToTmpPath(context, bitmap);
        } catch (IOException e) {
            String message = "saveImageToTmpPath >> 压缩图片到指定大小以内用于附件上传失败";
            Log.e(TAG, message, e);
            CommonUtils.debugToast(context, message);
            return null;
        }
    }

    /**
     * 获取图片的 Content Uri
     *
     * @param context   上下文
     * @param imageFile 图片文件
     * @return 图片的 Content Uri
     */
    public static Uri getImageContentUri(Context context, File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.MediaColumns._ID));
                Uri baseUri = Uri.parse("content://media/external/images/media");
                return Uri.withAppendedPath(baseUri, "" + id);
            } else {
                if (imageFile.exists()) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DATA, filePath);
                    return context.getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } else {
                    return null;
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public static Uri compressImageNew(Context context, Uri oriImageUri, long oriFileSize, long distFileSize) {
        try {
            // 压缩图片到指定大小
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), oriImageUri);

            double scale = Math.sqrt(distFileSize * 1.0 / oriFileSize);

            // 创建操作图片用的 matrix 对象
            Matrix matrix = new Matrix();

            // 缩放图片动作
            matrix.postScale((float) scale, (float) scale);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);

            return saveImageToTmpPath(context, bitmap);
        } catch (IOException e) {
            String message = "saveImageToTmpPath >> 压缩图片到指定大小以内用于附件上传失败";
            Log.e(TAG, message, e);
            CommonUtils.debugToast(context, message);
            return null;
        }
    }

    /* START - 尺寸、像素相关 */

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp      A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static float convertDpToPixel(float dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px      A value in px (pixels) unit. Which we need to convert into db
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent dp equivalent to px value
     */
    public static float convertPixelsToDp(float px, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float dp = px / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return dp;
    }

    /* END - 尺寸、像素相关 */

    public static boolean isValidEmailAddress(String email) {
        if (email == null || email.equals("")) return false;
        String ePattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(ePattern);
        java.util.regex.Matcher m = p.matcher(email);
        return m.matches();
    }

    public static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(label, text);
        clipboardManager.setPrimaryClip(clipData);
    }

    /**
     * Cropping circular area from bitmap
     *
     * @param bitmap original bitmap
     * @return cropped bitmap
     */
    public static Bitmap getCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        //Bitmap _bmp = Bitmap.createScaledBitmap(output, 60, 60, false);
        //return _bmp;
        return output;
    }

    private final static Pangu pangu = new Pangu();

    public static String addSpaces(String text) {
        if (BUApplication.enableSpaceBetweenCNAndEN) return pangu.spacingText(text);
        else return text;
    }
}