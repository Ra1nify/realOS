package defpackage;

/* JADX INFO: compiled from: r8-map-id-f071591cd7900d703f8f4e591da4616fbd071031ee1e78187301dacbe5cf29c4 */
/* JADX INFO: loaded from: classes2.dex */
public final class zg6 {
    public static final java.lang.Object n = null;
    public static final android.net.Uri o = null;
    public static volatile defpackage.zg6 p;
    public final long a;
    public final android.content.Context b;
    public long c;
    public boolean d;
    public java.lang.String e;
    public java.lang.String f;
    public java.lang.String g;
    public com.oplus.util.OplusUnitConversionUtils h;
    public float i;
    public java.lang.String j;
    public java.lang.String k;
    public boolean l;
    public final boolean m;

    static {
            java.lang.Object r0 = new java.lang.Object
            r0.<init>()
            defpackage.zg6.n = r0
            java.lang.String r0 = "content://0@com.oplus.athena.database"
            android.net.Uri r0 = android.net.Uri.parse(r0)
            defpackage.zg6.o = r0
            return
    }

    public zg6(android.content.Context r9) {
            r8 = this;
            r8.<init>()
            xg6 r0 = new xg6
            android.os.Handler r1 = new android.os.Handler
            android.os.Looper r2 = android.os.Looper.getMainLooper()
            r1.<init>(r2)
            r0.<init>(r8, r1)
            r1 = 0
            r8.c = r1
            java.lang.String r1 = ""
            r8.e = r1
            r8.f = r1
            r8.g = r1
            r2 = -1082130432(0xffffffffbf800000, float:-1.0)
            r8.i = r2
            r8.j = r1
            r8.k = r1
            r1 = 0
            r8.l = r1
            r8.m = r1
            boolean r2 = r9 instanceof android.app.Application
            if (r2 == 0) goto L31
            r8.b = r9
            goto L37
        L31:
            android.content.Context r9 = r9.getApplicationContext()
            r8.b = r9
        L37:
            java.lang.String r2 = "activity"
            java.lang.Object r2 = r9.getSystemService(r2)
            android.app.ActivityManager r2 = (android.app.ActivityManager) r2
            android.app.ActivityManager$MemoryInfo r3 = new android.app.ActivityManager$MemoryInfo
            r3.<init>()
            r2.getMemoryInfo(r3)
            long r2 = r3.totalMem
            r8.a = r2
            int r2 = defpackage.ypa.a
            java.lang.StringBuilder r3 = new java.lang.StringBuilder
            java.lang.String r4 = "judgeWhetherAllowMemoDisplay(), LauncherModeManager.isOtaVersionUpdate()="
            r3.<init>(r4)
            boolean r4 = defpackage.op5.C
            java.lang.String r5 = ", apiLevel="
            java.lang.String r3 = defpackage.br0.f(r3, r4, r5, r2)
            r4 = 0
            java.lang.String r5 = "MemoryInfoManager"
            defpackage.q36.d(r4, r5, r3, r4)
            java.lang.String r3 = "allow_memory_info_display"
            r6 = 1
            if (r2 != 0) goto L87
            android.content.ContentResolver r2 = r9.getContentResolver()
            r7 = -1
            int r2 = android.provider.Settings.Secure.getIntForUser(r2, r3, r7, r1)
            java.lang.String r7 = "judgeWhetherAllowMemoDisplay(), isAllowMemoryInfoDisplay="
            defpackage.tu1.e(r7, r2, r4, r5, r4)
            boolean r7 = defpackage.op5.C
            if (r7 == 0) goto L80
            if (r2 == 0) goto L7c
            goto L7d
        L7c:
            r6 = r1
        L7d:
            r8.m = r6
            goto L91
        L80:
            if (r2 != r6) goto L83
            goto L84
        L83:
            r6 = r1
        L84:
            r8.m = r6
            goto L91
        L87:
            r7 = 34
            if (r2 <= r7) goto L8f
            r8.m = r1
            r6 = r1
            goto L91
        L8f:
            r8.m = r6
        L91:
            android.content.ContentResolver r2 = r9.getContentResolver()
            android.provider.Settings.Secure.putIntForUser(r2, r3, r6, r1)
            r8.h()
            android.content.ContentResolver r8 = r9.getContentResolver()     // Catch: java.lang.Exception -> Lad
            java.lang.String r9 = "display_memory_information_recent_task"
            android.net.Uri r9 = android.provider.Settings.Secure.getUriFor(r9)     // Catch: java.lang.Exception -> Lad
            int r2 = android.os.UserHandle.myUserId()     // Catch: java.lang.Exception -> Lad
            r8.registerContentObserver(r9, r1, r0, r2)     // Catch: java.lang.Exception -> Lad
            return
        Lad:
            r8 = move-exception
            java.lang.String r9 = "registerObserver(), e="
            defpackage.f04.f(r9, r8, r4, r5, r4)
            return
    }

    public static defpackage.zg6 a(android.content.Context r2) {
            zg6 r0 = defpackage.zg6.p
            if (r0 != 0) goto L19
            java.lang.Object r0 = defpackage.zg6.n
            monitor-enter(r0)
            zg6 r1 = defpackage.zg6.p     // Catch: java.lang.Throwable -> L13
            if (r1 != 0) goto L15
            zg6 r1 = new zg6     // Catch: java.lang.Throwable -> L13
            r1.<init>(r2)     // Catch: java.lang.Throwable -> L13
            defpackage.zg6.p = r1     // Catch: java.lang.Throwable -> L13
            goto L15
        L13:
            r2 = move-exception
            goto L17
        L15:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L13
            goto L19
        L17:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L13
            throw r2
        L19:
            zg6 r2 = defpackage.zg6.p
            return r2
    }

    public static long e() {
            r0 = 1
            long[] r0 = new long[r0]
            java.lang.String r1 = "MemTotal:"
            java.lang.String[] r1 = new java.lang.String[]{r1}
            java.lang.String r2 = "/proc/meminfo"
            android.os.Process.readProcLines(r2, r1, r0)
            r1 = 0
            r0 = r0[r1]
            double r0 = (double) r0
            r2 = 4697254411347427328(0x4130000000000000, double:1048576.0)
            double r0 = r0 / r2
            double r0 = java.lang.Math.ceil(r0)
            long r0 = (long) r0
            return r0
    }

    public static long g(long r4) {
            r0 = 0
            int r2 = (r4 > r0 ? 1 : (r4 == r0 ? 0 : -1))
            if (r2 >= 0) goto L7
            r4 = r0
        L7:
            r0 = 1073741824(0x40000000, double:5.304989477E-315)
            long r2 = r4 / r0
            int r2 = (int) r2
            long r4 = r4 % r0
            int r4 = (int) r4
            if (r4 <= 0) goto L13
            int r2 = r2 + 1
        L13:
            if (r4 <= 0) goto L2f
            r4 = 1
            if (r2 == r4) goto L2f
            r4 = 3
            if (r2 == r4) goto L2f
            int r4 = r2 % 2
            if (r4 == 0) goto L21
            int r2 = r2 + 1
        L21:
            java.lang.String r5 = "formatMemoryDisplay: remainder: "
            java.lang.String r3 = ", mul: "
            java.lang.String r4 = defpackage.m24.a(r4, r2, r5, r3)
            r5 = 0
            java.lang.String r3 = "MemoryInfoManager"
            defpackage.q36.d(r5, r3, r4, r5)
        L2f:
            long r4 = (long) r2
            long r4 = r4 * r0
            return r4
    }

    public final int b() {
            r0 = this;
            boolean r0 = r0.d
            return r0
    }

    public final defpackage.yg6 c(boolean r26) {
            r25 = this;
            r1 = r25
            java.lang.String r2 = ""
            android.content.Context r3 = r1.b
            java.lang.String r4 = "\u200e"
            java.lang.String r5 = " "
            java.lang.String r6 = "format number exception, e="
            java.lang.String r0 = "persist.sys.oplus.nandswap.condition"
            java.lang.String r7 = "false"
            java.lang.String r0 = android.os.SystemProperties.get(r0, r7)
            java.lang.String r7 = "true"
            boolean r0 = r7.equals(r0)
            r7 = 0
            r8 = 0
            java.lang.String r9 = "MemoryInfoManager"
            if (r0 == 0) goto L44
            java.lang.String r0 = "persist.sys.oplus.nandswap.swapsize.curr"
            java.lang.String r10 = "0"
            java.lang.String r0 = android.os.SystemProperties.get(r0, r10)
            java.lang.String r10 = "getExpandCurr value:"
            defpackage.l00.h(r10, r0, r8, r9, r8)
            boolean r10 = android.text.TextUtils.isEmpty(r0)
            if (r10 != 0) goto L3c
            java.lang.String r10 = "[0-9]*"
            boolean r10 = java.util.regex.Pattern.matches(r10, r0)
            goto L3d
        L3c:
            r10 = r7
        L3d:
            if (r10 == 0) goto L44
            int r0 = java.lang.Integer.parseInt(r0)
            goto L4a
        L44:
            java.lang.String r0 = "getExpandCurr value:0"
            defpackage.q36.d(r8, r9, r0, r8)
            r0 = r7
        L4a:
            long r10 = (long) r0
            r1.c = r10
            android.content.ContentResolver r0 = r3.getContentResolver()     // Catch: java.lang.Exception -> L73
            android.net.Uri r12 = defpackage.zg6.o     // Catch: java.lang.Exception -> L73
            android.content.ContentProviderClient r12 = r0.acquireUnstableContentProviderClient(r12)     // Catch: java.lang.Exception -> L73
            if (r12 == 0) goto L77
            java.lang.String r0 = "get_rom2ram_pinned_bytes"
            android.os.Bundle r0 = r12.call(r0, r8, r8)     // Catch: java.lang.Throwable -> L68
            if (r0 == 0) goto L77
            java.lang.String r13 = "pinned_bytes"
            long r13 = r0.getLong(r13)     // Catch: java.lang.Throwable -> L68
            goto L79
        L68:
            r0 = move-exception
            r13 = r0
            r12.close()     // Catch: java.lang.Throwable -> L6e
            goto L72
        L6e:
            r0 = move-exception
            r13.addSuppressed(r0)     // Catch: java.lang.Exception -> L73
        L72:
            throw r13     // Catch: java.lang.Exception -> L73
        L73:
            r0 = move-exception
            r13 = 0
            goto L80
        L77:
            r13 = 0
        L79:
            if (r12 == 0) goto L85
            r12.close()     // Catch: java.lang.Exception -> L7f
            goto L85
        L7f:
            r0 = move-exception
        L80:
            java.lang.String r12 = "getMemoryInfo(), fail to get rom2ram, e="
            defpackage.f04.f(r12, r0, r8, r9, r8)
        L85:
            r12 = 1
            long[] r0 = new long[r12]
            java.lang.String r15 = "MemAvailable:"
            java.lang.String[] r15 = new java.lang.String[]{r15}
            r16 = 0
            java.lang.String r10 = "/proc/meminfo"
            android.os.Process.readProcLines(r10, r15, r0)
            r10 = r0[r7]
            boolean r0 = defpackage.q36.s()
            if (r0 == 0) goto La6
            java.lang.String r0 = "getAvailProcMemInfo: "
            java.lang.String r0 = defpackage.l00.c(r0, r10)
            defpackage.q36.d(r8, r9, r0, r8)
        La6:
            r18 = 4652218415073722368(0x4090000000000000, double:1024.0)
            double r10 = (double) r10
            double r10 = r10 * r18
            long r10 = (long) r10
            r15 = r7
            long r7 = r1.c
            r19 = 1073741824(0x40000000, double:5.304989477E-315)
            long r7 = r7 * r19
            long r7 = r7 + r10
            long r7 = r7 + r13
            java.lang.String r0 = "getMemoryInfo: total="
            r21 = r15
            java.lang.String r15 = ", available="
            r22 = r13
            long r12 = r1.a
            java.lang.StringBuilder r0 = defpackage.e.e(r0, r15, r12)
            r0.append(r7)
            java.lang.String r15 = ", availProcMem="
            java.lang.String r14 = ", rom2ram="
            defpackage.tu1.i(r0, r15, r10, r14)
            r10 = r22
            r0.append(r10)
            java.lang.String r10 = ", ExpandRamBytes="
            r0.append(r10)
            long r10 = r1.c
            long r10 = r10 * r19
            r0.append(r10)
            java.lang.String r10 = ", isConfidential="
            r0.append(r10)
            boolean r10 = defpackage.gp3.d
            r0.append(r10)
            java.lang.String r10 = ", enlargeSize="
            r0.append(r10)
            long r10 = r1.c
            r0.append(r10)
            java.lang.String r0 = r0.toString()
            r10 = 0
            defpackage.q36.d(r10, r9, r0, r10)
            long r10 = g(r12)     // Catch: java.lang.Exception -> L10b
            java.lang.String r0 = r1.f(r10)     // Catch: java.lang.Exception -> L10b
            boolean r10 = defpackage.gp3.d     // Catch: java.lang.Exception -> L10b
            if (r10 == 0) goto L109
            java.lang.String r0 = "16.0 GB"
        L109:
            r10 = r0
            goto L10e
        L10b:
            r0 = move-exception
            goto L26d
        L10e:
            java.lang.String r7 = r1.f(r7)     // Catch: java.lang.Exception -> L10b
            boolean r0 = android.text.TextUtils.isEmpty(r7)     // Catch: java.lang.Exception -> L10b
            if (r0 != 0) goto L14d
            java.lang.String[] r8 = r7.split(r5)     // Catch: java.lang.Exception -> L10b
            int r0 = r8.length     // Catch: java.lang.Exception -> L10b
            r14 = 1
            if (r0 <= r14) goto L14d
            r0 = r8[r21]     // Catch: java.lang.Exception -> L10b java.lang.NumberFormatException -> L127
            float r0 = java.lang.Float.parseFloat(r0)     // Catch: java.lang.Exception -> L10b java.lang.NumberFormatException -> L127
            goto L13a
        L127:
            r0 = move-exception
            java.lang.StringBuilder r11 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r11.<init>(r6)     // Catch: java.lang.Exception -> L10b
            r11.append(r0)     // Catch: java.lang.Exception -> L10b
            java.lang.String r0 = r11.toString()     // Catch: java.lang.Exception -> L10b
            r6 = 0
            defpackage.q36.d(r6, r9, r0, r6)     // Catch: java.lang.Exception -> L10b
            r0 = -1082130432(0xffffffffbf800000, float:-1.0)
        L13a:
            r1.i = r0     // Catch: java.lang.Exception -> L10b
            java.lang.StringBuilder r0 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r0.<init>(r5)     // Catch: java.lang.Exception -> L10b
            r14 = 1
            r5 = r8[r14]     // Catch: java.lang.Exception -> L10b
            r0.append(r5)     // Catch: java.lang.Exception -> L10b
            java.lang.String r0 = r0.toString()     // Catch: java.lang.Exception -> L10b
            r1.j = r0     // Catch: java.lang.Exception -> L10b
        L14d:
            long r5 = r1.c     // Catch: java.lang.Exception -> L10b
            long r5 = r5 * r19
            java.lang.String r0 = r1.f(r5)     // Catch: java.lang.Exception -> L10b
            if (r26 == 0) goto L1ab
            r15 = r21
            r1.l = r15     // Catch: java.lang.Exception -> L10b
            java.util.Locale r5 = java.util.Locale.getDefault()     // Catch: java.lang.Exception -> L10b
            java.lang.String r5 = r5.getLanguage()     // Catch: java.lang.Exception -> L10b
            java.lang.String r6 = "ug"
            boolean r5 = r5.equals(r6)     // Catch: java.lang.Exception -> L10b
            r24 = 2
            if (r5 != 0) goto L183
            java.util.Locale r5 = java.util.Locale.getDefault()     // Catch: java.lang.Exception -> L10b
            java.lang.String r5 = r5.getLanguage()     // Catch: java.lang.Exception -> L10b
            java.lang.String r6 = "ur"
            boolean r5 = r5.equals(r6)     // Catch: java.lang.Exception -> L10b
            if (r5 == 0) goto L180
            goto L183
        L180:
            r4 = r24
            goto L1b0
        L183:
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>(r4)     // Catch: java.lang.Exception -> L10b
            r5.append(r10)     // Catch: java.lang.Exception -> L10b
            java.lang.String r10 = r5.toString()     // Catch: java.lang.Exception -> L10b
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>(r4)     // Catch: java.lang.Exception -> L10b
            r5.append(r7)     // Catch: java.lang.Exception -> L10b
            java.lang.String r7 = r5.toString()     // Catch: java.lang.Exception -> L10b
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>(r4)     // Catch: java.lang.Exception -> L10b
            r5.append(r0)     // Catch: java.lang.Exception -> L10b
            java.lang.String r0 = r5.toString()     // Catch: java.lang.Exception -> L10b
            r14 = 1
            r1.l = r14     // Catch: java.lang.Exception -> L10b
            goto L180
        L1ab:
            r15 = r21
            r1.l = r15     // Catch: java.lang.Exception -> L10b
            r4 = 1
        L1b0:
            java.lang.Object[] r5 = new java.lang.Object[]{r7}     // Catch: java.lang.Exception -> L10b
            r6 = 2131953257(0x7f130669, float:1.954298E38)
            java.lang.String r3 = r3.getString(r6, r5)     // Catch: java.lang.Exception -> L10b
            boolean r5 = defpackage.q36.s()     // Catch: java.lang.Exception -> L10b
            java.lang.String r6 = ", mMemoryAvailableWithoutUnit: "
            java.lang.String r8 = ",strAvai:"
            java.lang.String r11 = "getMemoryInfo: strTotal:"
            if (r5 == 0) goto L1ef
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>(r11)     // Catch: java.lang.Exception -> L10b
            r5.append(r10)     // Catch: java.lang.Exception -> L10b
            r5.append(r8)     // Catch: java.lang.Exception -> L10b
            r5.append(r7)     // Catch: java.lang.Exception -> L10b
            java.lang.String r12 = ",mExpandRam:"
            r5.append(r12)     // Catch: java.lang.Exception -> L10b
            long r12 = r1.c     // Catch: java.lang.Exception -> L10b
            r5.append(r12)     // Catch: java.lang.Exception -> L10b
            r5.append(r6)     // Catch: java.lang.Exception -> L10b
            float r12 = r1.i     // Catch: java.lang.Exception -> L10b
            r5.append(r12)     // Catch: java.lang.Exception -> L10b
            java.lang.String r5 = r5.toString()     // Catch: java.lang.Exception -> L10b
            r12 = 0
            defpackage.q36.d(r12, r9, r5, r12)     // Catch: java.lang.Exception -> L10b
        L1ef:
            long r12 = r1.c     // Catch: java.lang.Exception -> L10b
            int r5 = (r12 > r16 ? 1 : (r12 == r16 ? 0 : -1))
            java.lang.String r12 = " | "
            if (r5 == 0) goto L21d
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>()     // Catch: java.lang.Exception -> L10b
            r5.append(r3)     // Catch: java.lang.Exception -> L10b
            r5.append(r12)     // Catch: java.lang.Exception -> L10b
            int r3 = r10.length()     // Catch: java.lang.Exception -> L10b
            int r3 = r3 + (-3)
            r15 = 0
            java.lang.String r3 = r10.substring(r15, r3)     // Catch: java.lang.Exception -> L10b
            r5.append(r3)     // Catch: java.lang.Exception -> L10b
            java.lang.String r3 = "+"
            r5.append(r3)     // Catch: java.lang.Exception -> L10b
            r5.append(r0)     // Catch: java.lang.Exception -> L10b
            java.lang.String r3 = r5.toString()     // Catch: java.lang.Exception -> L10b
            goto L22f
        L21d:
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>()     // Catch: java.lang.Exception -> L10b
            r5.append(r3)     // Catch: java.lang.Exception -> L10b
            r5.append(r12)     // Catch: java.lang.Exception -> L10b
            r5.append(r10)     // Catch: java.lang.Exception -> L10b
            java.lang.String r3 = r5.toString()     // Catch: java.lang.Exception -> L10b
        L22f:
            boolean r5 = defpackage.q36.s()     // Catch: java.lang.Exception -> L10b
            if (r5 == 0) goto L25b
            java.lang.StringBuilder r5 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L10b
            r5.<init>(r11)     // Catch: java.lang.Exception -> L10b
            r5.append(r10)     // Catch: java.lang.Exception -> L10b
            r5.append(r8)     // Catch: java.lang.Exception -> L10b
            r5.append(r7)     // Catch: java.lang.Exception -> L10b
            java.lang.String r8 = ",strEnLargeSize:"
            r5.append(r8)     // Catch: java.lang.Exception -> L10b
            r5.append(r0)     // Catch: java.lang.Exception -> L10b
            r5.append(r6)     // Catch: java.lang.Exception -> L10b
            float r6 = r1.i     // Catch: java.lang.Exception -> L10b
            r5.append(r6)     // Catch: java.lang.Exception -> L10b
            java.lang.String r5 = r5.toString()     // Catch: java.lang.Exception -> L10b
            r6 = 0
            defpackage.q36.d(r6, r9, r5, r6)     // Catch: java.lang.Exception -> L10b
        L25b:
            r1.e = r7     // Catch: java.lang.Exception -> L10b
            r1.f = r10     // Catch: java.lang.Exception -> L10b
            r1.g = r0     // Catch: java.lang.Exception -> L10b
            java.lang.String r0 = r3.replaceFirst(r7, r2)     // Catch: java.lang.Exception -> L10b
            r1.k = r0     // Catch: java.lang.Exception -> L10b
            yg6 r0 = new yg6     // Catch: java.lang.Exception -> L10b
            r0.<init>(r3, r4)     // Catch: java.lang.Exception -> L10b
            return r0
        L26d:
            java.lang.StringBuilder r1 = new java.lang.StringBuilder
            java.lang.String r3 = "getMemoryInfo(), e="
            r1.<init>(r3)
            r1.append(r0)
            java.lang.String r0 = r1.toString()
            r6 = 0
            defpackage.q36.d(r6, r9, r0, r6)
            yg6 r0 = new yg6
            r14 = 1
            r0.<init>(r2, r14)
            return r0
    }

    public final int d() {
            r4 = this;
            long r0 = r4.a
            long r0 = g(r0)
            r2 = 1073741824(0x40000000, double:5.304989477E-315)
            long r0 = r0 / r2
            int r4 = (int) r0
            return r4
    }

    public final java.lang.String f(long r3) {
            r2 = this;
            com.oplus.util.OplusUnitConversionUtils r0 = r2.h
            if (r0 != 0) goto Ld
            com.oplus.util.OplusUnitConversionUtils r0 = new com.oplus.util.OplusUnitConversionUtils
            android.content.Context r1 = r2.b
            r0.<init>(r1)
            r2.h = r0
        Ld:
            java.lang.String r2 = r0.getUnitValue(r3)
            return r2
    }

    public final void h() {
            r7 = this;
            java.lang.String r0 = "MemoryInfoManager"
            java.lang.String r1 = "updateMemoDisplaySwitch mNeedMemoryDetail = "
            r2 = 0
            android.content.Context r3 = r7.b     // Catch: java.lang.Exception -> L3d
            android.content.ContentResolver r3 = r3.getContentResolver()     // Catch: java.lang.Exception -> L3d
            java.lang.String r4 = "display_memory_information_recent_task"
            r5 = 0
            int r3 = android.provider.Settings.Secure.getInt(r3, r4, r5)     // Catch: java.lang.Exception -> L3d
            r4 = 1
            if (r3 != r4) goto L18
            r3 = r4
            goto L19
        L18:
            r3 = r5
        L19:
            r7.d = r3     // Catch: java.lang.Exception -> L3d
            boolean r3 = defpackage.q36.s()     // Catch: java.lang.Exception -> L3d
            boolean r6 = r7.m
            if (r3 == 0) goto L3f
            java.lang.StringBuilder r3 = new java.lang.StringBuilder     // Catch: java.lang.Exception -> L3d
            r3.<init>(r1)     // Catch: java.lang.Exception -> L3d
            boolean r1 = r7.d     // Catch: java.lang.Exception -> L3d
            r3.append(r1)     // Catch: java.lang.Exception -> L3d
            java.lang.String r1 = ", mAllowMemoryInfoDisplay="
            r3.append(r1)     // Catch: java.lang.Exception -> L3d
            r3.append(r6)     // Catch: java.lang.Exception -> L3d
            java.lang.String r1 = r3.toString()     // Catch: java.lang.Exception -> L3d
            defpackage.q36.d(r2, r0, r1, r2)     // Catch: java.lang.Exception -> L3d
            goto L3f
        L3d:
            r7 = move-exception
            goto L49
        L3f:
            boolean r1 = r7.d     // Catch: java.lang.Exception -> L3d
            if (r1 == 0) goto L46
            if (r6 == 0) goto L46
            r5 = r4
        L46:
            r7.d = r5     // Catch: java.lang.Exception -> L3d
            return
        L49:
            java.lang.String r1 = "updateMemoDisplaySwitch(), e="
            defpackage.f04.f(r1, r7, r2, r0, r2)
            return
    }
}
