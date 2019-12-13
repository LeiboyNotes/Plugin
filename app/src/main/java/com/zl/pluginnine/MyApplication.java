package com.zl.pluginnine;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            hookAMS();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //第二次hook
        try {
            hookActivityThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * AMS 检查之前
     *
     * @throws
     */
    private void hookAMS() throws Exception {

        Class mActivityManagerClass = Class.forName("android.app.ActivityManager");
        Field mIActivityManagerSingletonField = mActivityManagerClass.getDeclaredField("IActivityManagerSingleton");
        mIActivityManagerSingletonField.setAccessible(true);
        //通过Field 去拿到此字段的对象
        Object mIActivityManagerSingleton = mIActivityManagerSingletonField.get(null);


        //反射Singleton
        Class mSingletonClass = Class.forName("android.util.Singleton");
        //为了拿到
        Field mInstanceField = mSingletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);

        //获取IActivityManager.aidl
        Class mIActivityManagerClass = Class.forName("android.app.IActivityManager");

        final Object mIActivityManager = mActivityManagerClass.getMethod("getService").invoke(null);

        //动态代理
        Object mIActivityManagerProxy = Proxy.newProxyInstance(getClassLoader(),
                new Class[]{mIActivityManagerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        //先执行我们的
                        if ("startActivity".equals(method.getName())) {

                            //代理的Activity
                            Intent proxyIntent = new Intent(MyApplication.this, ProxyActivity.class);

                            //真正的LoginActivity 携带过去
                            proxyIntent.putExtra("target", ((Intent) args[2]));//LoginActivity ==(Intent)args[2]

                            args[2] = proxyIntent;//换成代理的
                        }
                        return method.invoke(mIActivityManager, args);
                    }
                });

        mInstanceField.set(mIActivityManagerSingleton, mIActivityManagerProxy);
    }

    /**
     * 真正加载的时候，把代理的Activity
     *
     * @throws
     */
    private void hookActivityThread() throws Exception {
        //Handler  先执行自己的   然后再执行ActivityThread里面的
        Field mCallbackFiled = Handler.class.getDeclaredField("mCallback");
        mCallbackFiled.setAccessible(true);

        //@ActivityThread
        Class mActivityThreadClass = Class.forName("android.app.ActivityThread");
        Field mHField = mActivityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);

        //    public static ActivityThread currentActivityThread() 拿到ActivityThread对象
        Object mActivityThread = mActivityThreadClass.getMethod("currentActivityThread").invoke(null);

        //把 mH 字段转变成对象
        Object mH = mHField.get(mActivityThread);

//        mCallbackFiled.set(执行Handler的对象,换成我们自己的监听);
        mCallbackFiled.set(mH, new MyCallback());
    }

    class MyCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {

            Log.d("MyApplication", "监听到了。。。。。。");

            //把代理的Activity 换成目标 LoginActivity
            try {
                Object mClientTransaction = msg.obj;

                Class mLaunchActivityItemClass = Class.forName("android.app.servertransaction.LaunchActivityItem");

                //字段对象
                Field mActivityCallbacksField = mClientTransaction.getClass().getDeclaredField("mActivityCallbacks");
                mActivityCallbacksField.setAccessible(true);

                //字段对象转成真正的对象
                List mActivityCallbacks = (List) mActivityCallbacksField.get(mClientTransaction);
                if (mActivityCallbacks.size() == 0) {
                    return false;
                }
                Object mClientTransactionItem = mActivityCallbacks.get(0);

                //再次验证是否有关联  LaunchActivityItem==mClientTransactionItem
                if (mLaunchActivityItemClass.isInstance(mClientTransactionItem)==false) {
                    return false;
                }
                Field mIntentField = mLaunchActivityItemClass.getDeclaredField("mIntent");
                mIntentField.setAccessible(true);

                //把目标的Activity 取出来  ProxyActivity
                Intent proxyIntent  = (Intent) mIntentField.get(mClientTransactionItem);
                Parcelable targetIntent = proxyIntent.getParcelableExtra("target");//从第一个Hook所携带的Intent

                if (targetIntent != null) {
                    mIntentField.set(mClientTransactionItem,targetIntent);
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;//继续加载系统的 正常执行
        }
    }
}
