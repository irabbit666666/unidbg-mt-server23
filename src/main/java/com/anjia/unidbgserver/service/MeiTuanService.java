package com.anjia.unidbgserver.service;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.anjia.unidbgserver.config.UnidbgProperties;
import com.anjia.unidbgserver.utils.TempFileUtils;
import com.anjia.unidbgserver.web.MeiTuanForm;
import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.EmulatorBuilder;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.DynarmicFactory;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.Enumeration;
import com.github.unidbg.linux.android.dvm.api.ApplicationInfo;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.jni.ProxyDvmObject;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;

import static com.anjia.unidbgserver.utils.PrintUtils.*;

import com.github.unidbg.memory.Memory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.*;

@Slf4j
public class MeiTuanService extends AbstractJni implements IOResolver {

    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;

    private final static String BASE_MEITUAN_PATH = "data/apks/meituan";
    private final static String MEITUAN_APK_PATH = BASE_MEITUAN_PATH + "/meituan.apk";

    private final UnidbgProperties unidbgProperties;
    private final DvmClass NBridge;

    // private final static String LIBTT_ENCRYPT_LIB_PATH = "data/apks/so/libttEncrypt.so";

    @SneakyThrows
    MeiTuanService(UnidbgProperties unidbgProperties) {
        this.unidbgProperties = unidbgProperties;
        // 创建模拟器实例，要模拟32位或者64位，在这里区分
        EmulatorBuilder<AndroidEmulator> builder = AndroidEmulatorBuilder.for32Bit().setProcessName("com.sankuai.meituan");
        // 动态引擎
        if (unidbgProperties.isDynarmic()) {
            builder.addBackendFactory(new DynarmicFactory(true));
        }
        emulator = builder.build();
        // 模拟器的内存操作接口
        final Memory memory = emulator.getMemory();
        // 设置系统类库解析
        memory.setLibraryResolver(new AndroidResolver(23));

        // 创建Android虚拟机
        // vm = emulator.createDalvikVM(); // 只创建vm，用来读so,不加载apk
        vm = emulator.createDalvikVM(TempFileUtils.getTempFile(MEITUAN_APK_PATH));
        // 设置是否打印Jni调用细节
        vm.setVerbose(unidbgProperties.isVerbose());
        vm.setJni(this);
        emulator.getSyscallHandler().addIOResolver(this);
        emulator.getSyscallHandler().setEnableThreadDispatcher(true);
        // 加载libttEncrypt.so到unicorn虚拟内存，加载成功以后会默认调用init_array等函数，这是直接读so文件
        // DalvikModule dm = vm.loadLibrary(TempFileUtils.getTempFile(LIBTT_ENCRYPT_LIB_PATH), false);
        // 这是搜索加载apk里的模块名，比如 libguard.so 那么模块名一般是guard
        DalvikModule dm = vm.loadLibrary("mtguard", false);
        // 手动执行JNI_OnLoad函数
        dm.callJNI_OnLoad(emulator);
        // 加载好的libttEncrypt.so对应为一个模块
        module = dm.getModule();

        dm.callJNI_OnLoad(emulator);
        NBridge = vm.resolveClass("com/meituan/android/common/mtguard/NBridge");
        initMain();

        // TTEncryptUtils = vm.resolveClass("com/bytedance/frameworks/core/encrypt/TTEncryptUtils");
    }

    /**
     * unidbg 模拟调用
     * <p>
     * meiTuanForm  meiTuanForm 入参
     *
     * @return 结果
     */
    public String doWork(MeiTuanForm meiTuanForm) {
        StringObject arg1 = new StringObject(vm, "9b69f861-e054-4bc4-9daf-d36ae205ed3e");
        String method = meiTuanForm.getMethod().toUpperCase();
        String api = iGetApi(meiTuanForm.getUrl());
        String params = iGetParams(meiTuanForm.getUrl());
        String postData = iGetPostData(meiTuanForm.getParams());
        String host = iGetHost(meiTuanForm.getUrl());
        //        ByteArray arg2 = new ByteArray(vm, ("GET /abtest/v1/getDivideStrategies __reqTraceID=0bb38434-745f-41ed-a18b-80b1409fe3b3&app=group&ci=1&layerKeys=2%2C1772%2C1586&p_appid=10&platform=Android&userId=-1&userid=-1&utm_campaign=AgroupBgroupC0E0Ghomepage&utm_medium=android&utm_source=qq&utm_term=1100200205&uuid=0000000000000145B8AC3CDC1418AA8A9CB0D113706910000000000000451327&version_name=11.20.205").getBytes(StandardCharsets.UTF_8));
        String pre_data = method + " " + api + " " + params + postData;
//        System.out.println(pre_data);
        ByteArray arg2 = new ByteArray(vm, (pre_data).getBytes(StandardCharsets.UTF_8));
        ByteArray arg3 = new ByteArray(vm, host.getBytes(StandardCharsets.UTF_8));
        ArrayObject retobj = NBridge.callStaticJniMethodObject(emulator, "main(I[Ljava/lang/Object;)[Ljava/lang/Object;", 2, new ArrayObject(arg1, arg2, arg3));
        String ret = retobj.getValue()[0].getValue().toString();
//        System.out.println(ret);
//        System.out.println(ret.length());
        return ret;
    }

    @SneakyThrows
    @Override
    public FileResult resolve(Emulator emulator, String pathname, int oflags) {
//        System.out.println("lilac open:"+pathname);
        if (pathname.equals("/data/app/com.sankuai.meituan-2nOCxLCJUl7lL3J_S7uSPA==/base.apk")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(MEITUAN_APK_PATH), pathname));
        }
        if (pathname.equals("/data/data/com.sankuai.meituan/files/.mtg_dfpid_com.sankuai.meituan")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/.mtg_dfpid_com.sankuai.meituan"), pathname));
        }
        if (pathname.equals("/system/bin/ls")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/ls"), pathname));
        }
        if (pathname.equals("/sys/class/power_supply/battery/temp")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/temp"), pathname));
        }
        if (pathname.equals("/sys/class/power_supply/battery/voltage_now")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/voltage_now"), pathname));
        }
        if (pathname.equals("/data/data/com.sankuai.meituan/files/.mtg_sequence")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/sequence.txt"), pathname));
        }
        if (pathname.equals("/proc/meminfo")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/meminfo.txt"), pathname));
        }
        if (pathname.equals("/proc/self/status")) {
            return FileResult.success(new ByteArrayFileIO(oflags, pathname, ("TracerPid: 0").getBytes()));
        }
        if (pathname.equals("/data/app/com.sankuai.meituan-1/lib/arm/libmtguard.so")) {
            return FileResult.success(new SimpleFileIO(oflags, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/libmtguard.so"), pathname));
        }
        if (pathname.equals("/sys/devices/system/cpu/")) {
            return FileResult.success(new DirectoryFileIO(oflags, pathname, TempFileUtils.getTempFile(BASE_MEITUAN_PATH + "/cpu")));
        }
        return null;
    }

    public void destroy() throws IOException {
        emulator.close();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public void initMain() {
        // so init
        NBridge.callStaticJniMethodObject(emulator, "main(I[Ljava/lang/Object;)[Ljava/lang/Object;", 1, ArrayObject.newStringArray(vm, "9b69f861-e054-4bc4-9daf-d36ae205ed3e"));
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "com/meituan/android/common/mtguard/NBridge->getClassLoader()Ljava/lang/ClassLoader;": {
                return vm.resolveClass("dalvik/system/PathClassLoader", vm.resolveClass("java/lang/ClassLoader")).newObject(null);
            }
            case "com/meituan/android/common/mtguard/NBridge->main2(I[Ljava/lang/Object;)Ljava/lang/Object;": {
                int cmd = vaList.getIntArg(0);
//                System.out.println("cmd:"+cmd);
                if (cmd == 4) {
                    //  return MTGuard.sPic;
                    return new StringObject(vm, "ms_com.sankuai.meituan");
                }
                if (cmd == 5) {
                    // return MTGuard.sSec;
                    return new StringObject(vm, "ppd_com.sankuai.meituan.xbt");
                }
                if (cmd == 1) {
                    // return MTGuard.sPackageName;
                    return new StringObject(vm, vm.getPackageName());
                }
                if (cmd == 2) {
                    // return MTGuard.sSystemContext;
                    return vm.resolveClass("android/content/Context").newObject(null);
                }
                if (cmd == 6) {
                    //return "5.9.8";
                    return new StringObject(vm, "5.9.8");
                }
                if (cmd == 41) {
                    // MTGlibInterface.raptorFakeAPI((String) objArr[0], ((Integer) objArr[1]).intValue(), ((Integer) objArr[2]).intValue());
                    // return null;
                    // 这就是典型的side effect
                    return null;
                }
                if (cmd == 3) {
                    // if (MTGuard.getAdapter() != null) {
                    //    return MTGuard.getAdapter().a;
                    //}
                    // maybe fix
                    return null;
//                    return vm.resolveClass("com.meituan.android.common.mtguard.c$a").newObject(null);
                }
                if (cmd == 51) {
                    //StringBuilder sb = new StringBuilder();
                    //sb.append(b.b());
                    //return sb.toString();
                    return new StringObject(vm, "2");
                }
                if (cmd == 8) {
                    // return MTGuard.DfpId;
                    return new StringObject(vm, "DAD755D674483021F206818DEBA6BC6EA8AF2E33DC527576E6E0CF35".toLowerCase());
                }
                if (cmd == 40) {
                    // a7,xid
                    return new StringObject(vm, "mQdbBQwCbnV0AjyUCvTxIFwTmnlLMCcZOoYL7mn9S/OnMFTbbaXkS/vOyUlB7ZnxoOnznY8gX6vj75iDx/vCJGlbJcwgJcBH77QxkDC9xFE=");
                }
                if (cmd == 32) {
                    return new StringObject(vm, "{\"health\":2,\"level\":100,\"plugged\":1,\"present\":true,\"scale\":100,\"status\":2,\"telephony\":\"Li-poly\",\"temperature\":2,\"voltage\":4334}");
                }
                if (cmd == 19) {
                    return new StringObject(vm, "ICM20690");
                }
                if (cmd == 17) {
                    return new StringObject(vm, "qualcomm");
                }
                if (cmd == 9) {
                    return new StringObject(vm, "0");
                }
                if (cmd == 26) {
                    return new StringObject(vm, "[2,100]");
                }
                if (cmd == 37) {
                    return new StringObject(vm, "");
                }
                if (cmd == 13) {
                    return new StringObject(vm, "qualcomm");
                }
                if (cmd == 11) {
                    // AccessibilityUtils.isAccessibilityEnable(MTGuard.getAdapter().a) ? "1" : "0";
                    return new StringObject(vm, "0");
                }
                if (cmd == 28) {
                    // AppInfoWorker.getFirstLaunchTime(MTGuard.getAdapter().a);
                    return new StringObject(vm, "1653727513999");
                }
                if (cmd == 29) {
                    // n.a(MTGuard.getAdapter().a).a();
                    return new StringObject(vm, "0000000000000553D587C99A040E298B69D8D1BF421DFA165427618326859910");
                }
                if (cmd == 46) {
                    // i.a(MTGuard.getAdapter().a).b();
                    return new StringObject(vm, "0.0000000000|0.0000000000");
                }
                if (cmd == 34) {
                    // l.b().toString();
                    return new StringObject(vm, "[]");
                }
                if (cmd == 35) {
                    // l.c().toString();
                    return new StringObject(vm, "[]");
                }
                if (cmd == 58) {
                    // MTGuard.getAdapter().c();
                    return new StringObject(vm, "");
                }
                if (cmd == 61) {
                    return null;
                }
                break;
            }
            case "java/lang/System->getProperty(Ljava/lang/String;)Ljava/lang/String;": {
                String propertyName = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(propertyName);
                if (propertyName.equals("java.io.tmpdir")) {
                    return new StringObject(vm, "/data/user/0/com.sankuai.meituan/cache");
                }
                if (propertyName.equals("http.proxyHost")) {
                    return null;
                }
                if (propertyName.equals("https.proxyHost")) {
                    return null;
                }
                if (propertyName.equals("http.proxyPort")) {
                    return null;
                }
                if (propertyName.equals("https.proxyPort")) {
                    return null;
                }
            }
            case "android/os/SystemProperties->get(Ljava/lang/String;)Ljava/lang/String;": {
                String propertName = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(propertName);
                if (propertName.equals("ro.build.id")) {
                    return new StringObject(vm, "QKQ1.190828.002");
                }
                if (propertName.equals("persist.sys.usb.config")) {
                    // irabbit!! 检测adb调试
//                    return new StringObject(vm, "adb");
                    return new StringObject(vm, "adb");
                }
                if (propertName.equals("sys.usb.config")) {
                    // irabbit!! 检测adb调试
//                    return new StringObject(vm, "adb");
                    return new StringObject(vm, "adb");
                }
                if (propertName.equals("sys.usb.state")) {
                    // irabbit!! 检测adb调试
//                    return new StringObject(vm, "adb");
                    return new StringObject(vm, "adb");
                }
                if (propertName.equals("ro.debuggable")) {
                    return new StringObject(vm, "0");
                }
                if (propertName.equals("gsm.sim.state")) {
                    return new StringObject(vm, "ABSENT,ABSENT");
                }
                if (propertName.equals("gsm.version.ril-impl")) {
                    return new StringObject(vm, "Qualcomm RIL 1.0");
                }
                if (propertName.equals("ro.secure")) {
                    return new StringObject(vm, "1");
                }
                if (propertName.equals("wifi.interface")) {
                    return new StringObject(vm, "wlan0");
                }
            }
            case "java/lang/ClassLoader->getSystemClassLoader()Ljava/lang/ClassLoader;": {
                return vm.resolveClass("java/lang/ClassLoader").newObject(null);
            }
            case "java/net/NetworkInterface->getNetworkInterfaces()Ljava/util/Enumeration;": {
                // 真实情况这个数组要长很多
                String[] NetworkInterfaceNameList = new String[]{"dummy0", "r_rmnet_data2", "r_rmnet_data3", "ip_vti0", "wlan0", "wlan1"};
                int length = NetworkInterfaceNameList.length;
                List<DvmObject<?>> NetworkInterfacelist = new ArrayList<>();

                for (int i = 0; i < length; i++) {
                    NetworkInterfacelist.add(vm.resolveClass("java/net/NetworkInterface").newObject(NetworkInterfaceNameList[i]));
                }

                return new Enumeration(vm, NetworkInterfacelist);
            }
            case "java/util/Collections->list(Ljava/util/Enumeration;)Ljava/util/ArrayList;":
                return new ArrayListObject(vm, (List<? extends DvmObject<?>>) vaList.getObjectArg(0).getValue());
            case "android/os/Environment->getExternalStorageDirectory()Ljava/io/File;": {
                return vm.resolveClass("java/io/File").newObject(signature);
            }
            case "android/os/Environment->getDataDirectory()Ljava/io/File;": {
                return vm.resolveClass("java/io/File").newObject(signature);
            }
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "java/lang/ClassLoader->loadClass(Ljava/lang/String;)Ljava/lang/Class;": {
                String clssName = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(clssName);
                if (clssName.equals("com/meituan/android/common/mtguard/NBridge")) {
                    return vm.resolveClass(clssName);
                }
                if (clssName.equals("de.robv.android.xposed.XposedBridge")) {
                    emulator.getDalvikVM().throwException(vm.resolveClass("java/lang/NoClassDefFoundError").newObject(clssName));
                    return null;
                }
                if (clssName.equals("com/meituan/android/privacy/interfaces/PermissionGuard")) {
                    return vm.resolveClass(clssName);
                }
            }
            case "android/app/ContextImpl->getPackageManager()Landroid/content/pm/PackageManager;": {
                return vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            }
            case "android/content/pm/PackageManager->getApplicationInfo(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;": {
                String appName = vaList.getObjectArg(0).getValue().toString();
                if (appName.equals("com.sankuai.meituan")) {
                    return new ApplicationInfo(vm);
                }
            }
            case "com/meituan/android/common/mtguard/c$a->getPackageManager()Landroid/content/pm/PackageManager;": {
                return vm.resolveClass("android/content/pm/PackageManager").newObject(null);
            }
            case "com/meituan/android/common/mtguard/c$a->getFilesDir()Ljava/io/File;": {
                return vm.resolveClass("java/io/File").newObject(null);
            }
            case "java/util/ArrayList->iterator()Ljava/util/Iterator;": {
                ArrayList arrayList = (ArrayList) dvmObject.getValue();
                return vm.resolveClass("java/util/Iterator").newObject(arrayList.iterator());
            }
            case "java/util/Iterator->next()Ljava/lang/Object;": {
                Iterator iterator = (Iterator) dvmObject.getValue();
                return vm.resolveClass("java/net/NetworkInterface").newObject(iterator.next());
            }
            case "java/net/NetworkInterface->getName()Ljava/lang/String;": {
                return new StringObject(vm, dvmObject.getValue().toString());
            }
            case "android/app/ContextImpl->getResources()Landroid/content/res/Resources;": {
                return vm.resolveClass("android/content/res/Resources").newObject(null);
            }
            case "android/content/res/Resources->getConfiguration()Landroid/content/res/Configuration;": {
                return vm.resolveClass("android/content/res/Configuration").newObject(null);
            }
            case "android/app/ContextImpl->getSystemService(Ljava/lang/String;)Ljava/lang/Object;": {
                String SystemServiceName = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(SystemServiceName);
                if (SystemServiceName.equals("display")) {
                    return vm.resolveClass("android.hardware.display.DisplayManager").newObject(null);
                }
                if (SystemServiceName.equals("audio")) {
                    return vm.resolveClass("android.media.AudioManager").newObject(null);
                }
                if (SystemServiceName.equals("location")) {
                    return vm.resolveClass("android.location.LocationManager").newObject(null);
                }
            }
            case "android/hardware/display/DisplayManager->getDisplay(I)Landroid/view/Display;": {
                return vm.resolveClass("android/view/Display").newObject(null);
            }
            case "java/io/File->getPath()Ljava/lang/String;": {
                String pathSource = dvmObject.getValue().toString();
                if (pathSource.contains("getExternalStorageDirectory")) {
                    return new StringObject(vm, "/storage/emulated/0");
                }
                if (pathSource.contains("getDataDirectory")) {
                    return new StringObject(vm, "/data");
                }
            }
            case "android/app/ContextImpl->getContentResolver()Landroid/content/ContentResolver;": {
                return vm.resolveClass("android/content/ContentResolver").newObject(null);
            }
            case "java/io/File->listFiles()[Ljava/io/File;": {
                return null;
            }
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/content/pm/ApplicationInfo->sourceDir:Ljava/lang/String;": {
                return new StringObject(vm, "/data/app/com.sankuai.meituan-2nOCxLCJUl7lL3J_S7uSPA==/base.apk");
            }
            case "android/content/res/Configuration->locale:Ljava/util/Locale;": {
                return ProxyDvmObject.createObject(vm, Locale.getDefault());
            }
        }
        return super.getObjectField(vm, dvmObject, signature);
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/content/pm/PackageInfo->versionCode:I": {
                return 1100200205;
            }
            case "android/util/DisplayMetrics->widthPixels:I": {
                return 1080;
            }
            case "android/util/DisplayMetrics->heightPixels:I": {
                return 2160;
            }
        }
        return super.getIntField(vm, dvmObject, signature);
    }

    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "java/lang/Integer-><init>(I)V": {
                return DvmInteger.valueOf(vm, vaList.getIntArg(0));
            }
            case "java/io/File-><init>(Ljava/lang/String;)V": {
                return vm.resolveClass("java/io/File").newObject(null);
            }
            case "java/lang/Boolean-><init>(Z)V": {
                boolean b;
                if (vaList.getIntArg(0) != 0) {
                    b = true;
                } else {
                    b = false;
                }
                return DvmBoolean.valueOf(vm, b);
            }
            case "android/util/DisplayMetrics-><init>()V": {
                return dvmClass.newObject(null);
            }
            case "android/os/StatFs-><init>(Ljava/lang/String;)V": {
                String path = vaList.getObjectArg(0).getValue().toString();
                return dvmClass.newObject(path);
            }
        }
        return super.newObjectV(vm, dvmClass, signature, vaList);
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "java/io/File->canRead()Z": {
                return true;
            }
            case "android/content/pm/PackageManager->hasSystemFeature(Ljava/lang/String;)Z": {
                String feature = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(feature);
                if (feature.equals("android.hardware.bluetooth")) {
                    return true;
                }
                if (feature.equals("android.hardware.location.gps")) {
                    return true;
                }
                if (feature.equals("gsm.sim.state")) {
                    return true;
                }
            }
            case "java/net/NetworkInterface->isUp()Z": {
                return true;
            }
        }
        return super.callBooleanMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build->BRAND:Ljava/lang/String;": {
                return new StringObject(vm, "Xiaomi");
            }
            case "android/os/Build->TYPE:Ljava/lang/String;": {
                return new StringObject(vm, "user");
            }
            case "android/os/Build->HARDWARE:Ljava/lang/String;": {
                return new StringObject(vm, "qcom");
            }
            case "android/os/Build->MODEL:Ljava/lang/String;": {
                return new StringObject(vm, "MIX 2S");
            }
            case "android/os/Build->TAGS:Ljava/lang/String;": {
                return new StringObject(vm, "release-keys");
            }
            case "android/os/Build$VERSION->RELEASE:Ljava/lang/String;": {
                return new StringObject(vm, "10");
            }
            case "android/os/Build->BOARD:Ljava/lang/String;": {
                return new StringObject(vm, "sdm845");
            }
            case "android/os/Build->MANUFACTURER:Ljava/lang/String;": {
                return new StringObject(vm, "Xiaomi");
            }
            case "android/os/Build->PRODUCT:Ljava/lang/String;": {
                return new StringObject(vm, "polaris");
            }
            case "android/os/Build->SUPPORTED_ABIS:[Ljava/lang/String;": {
                return ArrayObject.newStringArray(vm, "arm64-v8a", "armeabi-v7a", "armeabi");
            }
            case "android/os/Build->DEVICE:Ljava/lang/String;": {
                return new StringObject(vm, "polaris");
            }
            case "android/os/Build->HOST:Ljava/lang/String;": {
                return new StringObject(vm, "c3-miui-ota-bd134.bj");
            }
            case "com/meituan/android/privacy/interfaces/PermissionGuard->PERMISSION_PHONE_READ:Ljava/lang/String;": {
                return new StringObject(vm, "Phone.read");
            }
        }
        return super.getStaticObjectField(vm, dvmClass, signature);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            // 获取Android版本
            case "android/os/Build$VERSION->SDK_INT:I": {
                return 30;
            }
            case "android/content/pm/PackageManager->GET_SIGNATURES:I": {
                return 64;
            }
        }
        return super.getStaticIntField(vm, dvmClass, signature);
    }

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "com/meituan/android/common/mtguard/NBridge->getPermissionState(Ljava/lang/String;)I": {
                String arg = vaList.getObjectArg(0).getValue().toString();
//                System.out.println(arg);
                switch (arg) {
                    case "android.permission.ACCESS_NETWORK_STATE": {
                        return 3;
                    }
                    case "android.permission.ACCESS_WIFI_STATE": {
                        return 3;
                    }
                }

            }
            case "com/meituan/android/common/mtguard/NBridge->getPermissionState(Ljava/lang/String;Ljava/lang/String;)I": {
                return 2;
            }
            case "android/provider/Settings$System->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I": {
                String arg = vaList.getObjectArg(1).getValue().toString();
//                System.out.println(arg);
                switch (arg) {
                    // https://developer.android.com/reference/android/provider/Settings.System#SCREEN_BRIGHTNESS
                    case "screen_brightness": {
                        return 45;
                    }
                }
            }
        }
        return super.callStaticIntMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "android/view/Display->getRealMetrics(Landroid/util/DisplayMetrics;)V": {
                return;
            }
        }
        super.callVoidMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "android/os/StatFs->getBlockSize()I": {
                return 4096;
            }
            case "android/os/StatFs->getBlockCount()I": {
                return 29048414;
            }
            case "android/view/Display->getStreamVolume(I)I": {
                return 6;
            }
            case "android/view/Display->getStreamMaxVolume(I)I": {
                return 15;
            }
            case "android/view/Display->getCallState()I": {
                //  "电话状态[0 无活动/1 响铃/2 摘机]"
                return 0;
            }
            case "android/media/AudioManager->getStreamVolume(I)I": {
                return 10;
            }
            case "android/media/AudioManager->getStreamMaxVolume(I)I": {
                return 15;
            }
        }
        return super.callIntMethodV(vm, dvmObject, signature, vaList);
    }

    // 应该没有副作用
    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "java/lang/System->currentTimeMillis()J": {
                return System.currentTimeMillis();
            }
        }
        return super.callStaticLongMethodV(vm, dvmClass, signature, vaList);
    }

    // 应该没有副作用
    @Override
    public boolean callStaticBooleanMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "android/os/Debug->isDebuggerConnected()Z": {
                return false;
            }
        }
        return super.callStaticBooleanMethodV(vm, dvmClass, signature, vaList);
    }

    public static String iGetApi(String url) {
        if (url != null) {
            //获取api
            String api = url.substring(url.indexOf(".com") + 4, url.indexOf("?"));
            System.out.println("api=>" + api);
            return api;
        }
        return null;
    }

    public static String iGetParams(String url) {
        if (url != null) {
            String params = url.substring(url.indexOf("?") + 1);
            JSONObject jsonObject = new JSONObject(new TreeMap<>());
            try {
                String[] paramsArray = params.split("&");
                for (String param : paramsArray) {
                    String[] paramArray = param.split("=");
                    if (paramArray.length == 2) {
                        jsonObject.put(paramArray[0], paramArray[1]);
                    } else {
                        jsonObject.put(paramArray[0], "");
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
//            System.out.println("paramsObject=>" + jsonObject);
            // jsonObject拼接成字符串
            StringBuilder outStr = new StringBuilder();
            for (String key : jsonObject.keySet()) {
                outStr.append(key).append("=").append(jsonObject.get(key)).append("&");
            }
            String paramsStr = outStr.substring(0, outStr.length() - 1);
//            System.out.println("paramsStr=>" + paramsStr);
            return paramsStr;
        }
        return null;
    }

    public static String iGetPostData(String params) {
        if (params != null) {
//            //params转换成json
//            JSONObject jsonObject = new JSONObject(new TreeMap<>());
//            try {
//                String[] paramsArray = params.split("&");
//                for (String param : paramsArray) {
//                    String[] paramArray = param.split("=");
//                    if (paramArray.length == 2) {
//                        jsonObject.put(paramArray[0], paramArray[1]);
//                    } else {
//                        jsonObject.put(paramArray[0], "");
//                    }
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//            System.out.println("jsonObject=>" + jsonObject);
//            StringBuilder outStr = new StringBuilder();
//            for (String key : jsonObject.keySet()) {
//                outStr.append(key).append("=").append(jsonObject.get(key)).append("&");
//            }
//            String paramsStr = outStr.substring(0, outStr.length() - 1);
            String paramsStr = params;
//            System.out.println("paramsStr=>" + paramsStr);
            return paramsStr;
        }
        return null;
    }

    public static String iGetHost(String url) {
        if (url != null) {
            String[] urls = url.split("/");
            if (urls.length >= 3) {
//                System.out.println("host==>" + urls[2]);
                return urls[2];
            }
        }
        return null;
    }

    public static void main(String[] args) {
        String url = "https://wmapi-mt.meituan.com/config/mrn/checkListV4?ci=527&utm_source=qq&utm_medium=android&utm_term=1100200205&version_name=11.20.205&utm_content=553d587c99a040e298b69d8d1bf421dfa165427618326859910&utm_campaign=AgroupBgroupC0E0Ghomepage&msid=553d587c99a040e298b69d8d1bf421dfa1654276183268599101654275603299&uuid=0000000000000553D587C99A040E298B69D8D1BF421DFA165427618326859910&userid=-1&p_appid=10&__reqTraceID=7df02aba-2d4c-4aad-8688-7ffe3d49b4dd";
        String payload = "app=group&app_version=1100200205&bundles=[]&channel=qq&mrn_version=3.1120.218&platform=Android&rnVersion=0.63.3&uuid=0000000000000553D587C99A040E298B69D8D1BF421DFA165427618326859910";
        iGetHost(url);
        iGetApi(url);
        iGetParams(url);
        iGetPostData(payload);
    }

}
