package com.zyj.app.imageload;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.zyj.app.imageload.Key.Key;
import com.zyj.app.imageload.Key.MD5Key;
import com.zyj.app.imageload.bean.ImageHolder;
import com.zyj.app.imageload.cache.DiskCacheFactory;
import com.zyj.app.imageload.cache.DiskLruCacheManager;
import com.zyj.app.imageload.cache.ExternalCacheDiskCacheFactory;
import com.zyj.app.imageload.cache.MemoryCache;
import com.zyj.app.imageload.cache.MemoryCacheFactory;
import com.zyj.app.imageload.load.HttpLoader;
import com.zyj.app.imageload.util.ImageUtil;
import com.zyj.app.imageload.util.LogUtil;
import com.zyj.app.imageload.util.MyTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Administrator on 2016/9/10.
 */
public class ImageLoad {

    private static volatile ImageLoad instance ;
    private ExecutorService mexecutors = null ;
    private int mThreadCount = 20 ;
    private DiskCacheFactory mdiskCacheFactory ;
    private MemoryCache mmemoryCache ;
    private Key mkey ;

    private ImageLoad(Context context ){
        //创建线程池
        mexecutors  = Executors.newFixedThreadPool( mThreadCount ) ;

        if ( mdiskCacheFactory == null ){
            mdiskCacheFactory = new ExternalCacheDiskCacheFactory( context ) ;
        }

        if ( mkey == null ){
            mkey = new MD5Key() ;
        }

        if ( mmemoryCache == null ){
            mmemoryCache = new MemoryCacheFactory( mkey ) ;
        }
    }

    public static ImageLoad get(Context context) {
        if ( instance == null) {
            synchronized (ImageLoad.class) {
                if (instance == null) {
                    instance = new ImageLoad( context ) ;
                }
            }
        }
        return instance ;
    }

    public void load( String urlString , ImageView imageView ){
        load( urlString , imageView , 0 );
    }

    public void load(String urlString , ImageView imageView , int placeHolder ){
        if ( urlString == null ) return;
        if ( imageView == null ) return;

        imageView.setTag( urlString );

        if ( placeHolder != 0 ){
            imageView.setImageResource( placeHolder );
        }

        //首先从内存中获取图片
        Bitmap bitmap = mmemoryCache.getBitmapFromCache( urlString ) ;
        if ( bitmap != null  ){
            ImageHolder imageHolder = new ImageHolder() ;
            imageHolder.imageView = imageView ;
            imageHolder.bitmap = bitmap ;
            imageHolder.urlString = urlString ;
            setBitmapToImage( imageHolder );
            LogUtil.d( "从内存中获取的bitmap 不为空" );
        }else {
            //从磁盘获取图片
            LogUtil.d( "从内存中获取的bitmap 为空" );
            getBitmapFromDisk( urlString , imageView );
        }
    }

    private void setBitmapToImage( ImageHolder imageHolder ){
        if ( imageHolder.imageView.getTag().toString().equals( imageHolder.urlString )){
            imageHolder.imageView.setImageBitmap( imageHolder.bitmap );
        }
    }


    private void getBitmapFromDisk( final String urlString , final ImageView imageView ){
        new MyTask<>().setTaskListener(new MyTask.TaskListener() {
            @Override
            public void start() {

            }

            @Override
            public void update(Object o) {

            }

            @Override
            public Object doInBackground(Object o) {
                return  DiskLruCacheManager.getCacheBitmap( mdiskCacheFactory ,  urlString , ImageUtil.getImageViewSize( imageView )  ) ;
            }

            @Override
            public void result(Object o) {
                Bitmap bitmap = (Bitmap) o;

                mmemoryCache.setBitmapToCache( urlString , bitmap  );

                if ( bitmap != null ){
                    ImageHolder imageHolder = new ImageHolder() ;
                    imageHolder.imageView = imageView ;
                    imageHolder.bitmap = bitmap ;
                    imageHolder.urlString = urlString ;
                    setBitmapToImage( imageHolder );
                    LogUtil.d( "从磁盘获取的bitmap 不为空" ); ;
                }else {
                    //从网络中获取
                    LogUtil.d( "从磁盘获取的bitmap 为空" ); ;
                    getBitmapFromNet(  urlString , imageView );
                }

            }
        }).executeOnExecutor( mexecutors , "") ;
    }

    private void getBitmapFromNet( final String urlString , final ImageView imageView ){
        new MyTask<>().setTaskListener(new MyTask.TaskListener<String,Integer,Bitmap>() {
            @Override
            public void start() {

            }

            @Override
            public void update(Integer integer) {

            }

            @Override
            public Bitmap doInBackground(String netUrl) {
                Bitmap bitmap = HttpLoader.load( urlString , ImageUtil.getImageViewSize( imageView )) ;

                if ( bitmap != null ){
                    //把缓存写入磁盘
                    setBitmapToDiskCache( urlString , bitmap  );

                    //把缓存写入内存
                    mmemoryCache.setBitmapToCache( urlString , bitmap );
                }

                return bitmap ;
            }

            @Override
            public void result(Bitmap bitmap) {
                if ( bitmap != null ){
                    ImageHolder imageHolder = new ImageHolder() ;
                    imageHolder.imageView = imageView ;
                    imageHolder.bitmap = bitmap ;
                    imageHolder.urlString = urlString ;
                    setBitmapToImage( imageHolder );
                    LogUtil.d( "从网络获取的bitmap 成功" ) ;
                }else {
                    LogUtil.d( "从网络获取的bitmap 为空" ); ;
                }
            }
        }).executeOnExecutor( mexecutors  , urlString ) ;

    }

    private void setBitmapToDiskCache(final String urlString , final Bitmap bitmap  ){
        new MyTask().setTaskListener(new MyTask.TaskListener() {
            @Override
            public void start() {

            }

            @Override
            public void update(Object o) {

            }

            @Override
            public Object doInBackground(Object o) {
                DiskLruCacheManager.setCacheBitmap( mdiskCacheFactory , urlString , bitmap );
                return null ;
            }

            @Override
            public void result(Object o) {

            }
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR , "" ) ;
    }

    /**
     * get diskCache size
     * @return
     */
    public long getDiskCacheSize(){
        return mdiskCacheFactory.getTotalCacheSize() ;
    }

    /**
     * clear diskCache
     * must be On BackgroundThread
     */
    public void clearDiskCache(){
        mdiskCacheFactory.clearCache();
    }

    /**
     * get memory cache zise
     * @return
     */
    public int getMemoryCacheSize(){
        return mmemoryCache.getMemoryCacheSize() ;
    }

    /**
     * clear memory cache
     * call this method on Android Main Thread
     */
    public void clearMemoryCache(){
        mmemoryCache.clearMemoryCache();
    }

    /**
     * 设置是否打开log日志
     * @param log
     */
    public void setLogEnable( boolean log){
        LogUtil.OPEN_LOG = log ;
    }

}
