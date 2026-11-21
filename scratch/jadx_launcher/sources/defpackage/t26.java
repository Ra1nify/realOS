package defpackage;

/* JADX INFO: compiled from: r8-map-id-f071591cd7900d703f8f4e591da4616fbd071031ee1e78187301dacbe5cf29c4 */
/* JADX INFO: loaded from: classes3.dex */
public class t26 extends defpackage.bm0 implements defpackage.dj8 {
    public static int T = 15;
    public androidx.preference.PreferenceCategory A;
    public androidx.preference.PreferenceCategory B;
    public android.content.Context C;
    public android.graphics.drawable.BitmapDrawable D;
    public com.coui.appcompat.preference.COUISwitchPreference E;
    public com.coui.appcompat.preference.COUISwitchPreference F;
    public com.coui.appcompat.preference.COUIPreferenceCategory G;
    public com.coui.appcompat.preference.COUIPreferenceCategory H;
    public com.coui.appcompat.preference.COUIPreferenceCategory I;
    public com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference J;
    public defpackage.x21 K;
    public android.widget.LinearLayout L;
    public androidx.recyclerview.widget.RecyclerView M;
    public boolean N;
    public boolean O;
    public boolean P;
    public boolean Q;
    public defpackage.d36 R;
    public java.util.ArrayList S;
    public androidx.preference.PreferenceCategory z;

    public t26() {
            r2 = this;
            r2.<init>()
            r0 = 0
            r2.z = r0
            r2.A = r0
            r2.B = r0
            r2.D = r0
            r2.E = r0
            r2.F = r0
            r2.H = r0
            r1 = 1
            r2.Q = r1
            r2.S = r0
            return
    }

    @Override // defpackage.dj8
    public final boolean a(androidx.preference.Preference r8, java.lang.Object r9) {
            r7 = this;
            java.lang.String r8 = r8.getKey()
            boolean r0 = r9 instanceof java.lang.Boolean
            r1 = 0
            if (r0 != 0) goto Lb
            goto Lb0
        Lb:
            java.lang.Boolean r9 = (java.lang.Boolean) r9
            boolean r9 = r9.booleanValue()
            java.lang.String r0 = "display_memory_information_recent_task"
            boolean r2 = r0.equals(r8)
            r3 = 1
            if (r2 == 0) goto L32
            d36 r7 = r7.R
            android.content.Context r7 = r7.b
            zg6 r7 = defpackage.zg6.a(r7)
            boolean r8 = r7.d
            if (r9 != r8) goto L27
            return r3
        L27:
            android.content.Context r7 = r7.b
            android.content.ContentResolver r7 = r7.getContentResolver()
            boolean r7 = android.provider.Settings.Secure.putInt(r7, r0, r9)
            return r7
        L32:
            java.lang.String r0 = "display_phone_manager_entrance"
            boolean r2 = r0.equals(r8)
            d36 r7 = r7.R
            r4 = 0
            java.lang.String r5 = "LockSettingPresenter"
            if (r2 == 0) goto L65
            r7.getClass()
            wc8 r8 = defpackage.wc8.a
            android.content.Context r7 = r7.b
            defpackage.im9.l(r7)
            boolean r7 = defpackage.wc8.d
            if (r9 != r7) goto L53
            java.lang.String r7 = "savePhoneManagerEntranceSwitch-   return"
            defpackage.q36.d(r4, r5, r7, r4)
            return r3
        L53:
            java.lang.String r7 = "savePhoneManagerEntranceSwitch-:"
            defpackage.e.i(r7, r9, r4, r5, r4)
            android.content.Context r7 = defpackage.wc8.f
            if (r7 == 0) goto L60
            android.content.ContentResolver r4 = r7.getContentResolver()
        L60:
            boolean r7 = android.provider.Settings.Secure.putInt(r4, r0, r9)
            return r7
        L65:
            android.util.ArrayMap r7 = r7.a
            java.lang.Object r7 = r7.get(r8)
            ls r7 = (defpackage.ls) r7
            if (r7 != 0) goto L70
            goto Lb0
        L70:
            java.lang.String r0 = defpackage.o10.f
            java.lang.String[] r8 = defpackage.n10.a(r8)
            if (r8 != 0) goto L79
            goto Lb0
        L79:
            r0 = r8[r1]
            r8 = r8[r3]
            int r8 = java.lang.Integer.parseInt(r8)
            java.lang.String r2 = com.android.common.util.g.n(r0)
            if (r2 == 0) goto L88
            r0 = r2
        L88:
            rw5 r2 = com.oplus.quickstep.applock.b.d
            com.oplus.quickstep.applock.b r2 = defpackage.si7.b()
            java.lang.String r6 = "updateEntry isLocked="
            defpackage.wy2.e(r6, r4, r5, r9)
            if (r9 == 0) goto Lb1
            eg9 r8 = r2.l(r0, r8, r4, r3)
            defpackage.si7.c(r8, r1)
            boolean r0 = com.oplus.quickstep.applock.b.g
            if (r0 == 0) goto Lac
            boolean r0 = r8 instanceof defpackage.cg9
            if (r0 == 0) goto Lb4
            com.oplus.quickstep.applock.Reason r8 = r8.a
            com.oplus.quickstep.applock.Reason r0 = com.oplus.quickstep.applock.Reason.s
            if (r8 == r0) goto Lb4
            goto Lb0
        Lac:
            boolean r8 = r8 instanceof defpackage.cg9
            if (r8 == 0) goto Lb4
        Lb0:
            return r1
        Lb1:
            r2.p(r8, r0, r1, r3)
        Lb4:
            com.coui.appcompat.preference.COUISwitchPreference r7 = r7.s
            r7.setChecked(r9)
            return r3
    }

    @Override // defpackage.bm0
    public final java.lang.String i() {
            r1 = this;
            r0 = 2131953252(0x7f130664, float:1.954297E38)
            java.lang.String r1 = r1.getString(r0)
            return r1
    }

    public final void k(boolean r4) {
            r3 = this;
            java.lang.String r0 = "enterPowerSaveMode isInPowerSavingModel : "
            r1 = 0
            java.lang.String r2 = "LockSettingFragment"
            defpackage.e.i(r0, r4, r1, r2, r1)
            r3.N = r4
            com.coui.appcompat.preference.COUIPreferenceCategory r0 = r3.G
            if (r0 == 0) goto L11
            r0.setVisible(r4)
        L11:
            if (r4 == 0) goto L27
            com.coui.appcompat.preference.COUIPreferenceCategory r3 = r3.G
            if (r3 == 0) goto L27
            java.lang.String r4 = "preference_power_save_mode_notice"
            androidx.preference.Preference r3 = r3.h(r4)
            com.oplus.quickstep.locksetting.ui.NoticePreference r3 = (com.oplus.quickstep.locksetting.ui.NoticePreference) r3
            if (r3 == 0) goto L27
            r4 = 1
            r3.T = r4
            r3.h()
        L27:
            return
    }

    @Override // defpackage.fq7, defpackage.oj8, androidx.fragment.app.n
    public final void onCreate(android.os.Bundle r4) {
            r3 = this;
            super.onCreate(r4)
            androidx.preference.PreferenceScreen r4 = r3.getPreferenceScreen()
            r0 = 1
            r4.s = r0
            java.lang.String r4 = "title_recent_layout_fold"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.coui.appcompat.preference.COUIPreferenceCategory r4 = (com.coui.appcompat.preference.COUIPreferenceCategory) r4
            r3.I = r4
            r1 = 0
            if (r4 == 0) goto L3a
            boolean r4 = defpackage.zq7.c()
            if (r4 != 0) goto L2c
            com.coui.appcompat.preference.COUIPreferenceCategory r4 = r3.I
            r4.setVisible(r1)
            androidx.preference.PreferenceScreen r4 = r3.getPreferenceScreen()
            com.coui.appcompat.preference.COUIPreferenceCategory r2 = r3.I
            r4.j(r2)
        L2c:
            boolean r4 = defpackage.uy.c1()
            if (r4 == 0) goto L3a
            com.coui.appcompat.preference.COUIPreferenceCategory r4 = r3.I
            r2 = 2131953289(0x7f130689, float:1.9543045E38)
            r4.setTitle(r2)
        L3a:
            java.lang.String r4 = "key_recent_style_select"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference r4 = (com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference) r4
            r3.J = r4
            if (r4 == 0) goto L8d
            boolean r4 = defpackage.zq7.c()
            com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference r2 = r3.J
            if (r4 != 0) goto L5e
            r2.setVisible(r1)
            androidx.preference.PreferenceScreen r4 = r3.getPreferenceScreen()
            com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference r0 = r3.J
            r4.j(r0)
            r4 = 0
            r3.J = r4
            goto L8d
        L5e:
            r2.setVisible(r0)
            com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference r4 = r3.J
            by8 r0 = r4.c
            if (r0 != 0) goto L78
            android.os.Handler r0 = new android.os.Handler
            android.os.Looper r2 = android.os.Looper.getMainLooper()
            r0.<init>(r2)
            by8 r2 = new by8
            r2.<init>(r4, r0)
            r4.c = r2
            r0 = r2
        L78:
            android.content.Context r4 = r4.getContext()
            if (r4 == 0) goto L8d
            android.content.ContentResolver r4 = r4.getContentResolver()
            if (r4 == 0) goto L8d
            java.lang.String r2 = "key_recent_style"
            android.net.Uri r2 = android.provider.Settings.Secure.getUriFor(r2)
            r4.registerContentObserver(r2, r1, r0)
        L8d:
            java.lang.String r4 = "category_locked_apps"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            androidx.preference.PreferenceCategory r4 = (androidx.preference.PreferenceCategory) r4
            r3.z = r4
            if (r4 == 0) goto L9c
            r4.setVisible(r1)
        L9c:
            java.lang.String r4 = "category_unlock_apps"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            androidx.preference.PreferenceCategory r4 = (androidx.preference.PreferenceCategory) r4
            r3.A = r4
            if (r4 == 0) goto Lab
            r4.setVisible(r1)
        Lab:
            java.lang.String r4 = "category_bottom_divider"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            androidx.preference.PreferenceCategory r4 = (androidx.preference.PreferenceCategory) r4
            r3.B = r4
            if (r4 == 0) goto Lba
            r4.setVisible(r1)
        Lba:
            java.lang.String r4 = "category_power_save_mode_notice"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.coui.appcompat.preference.COUIPreferenceCategory r4 = (com.coui.appcompat.preference.COUIPreferenceCategory) r4
            r3.G = r4
            if (r4 == 0) goto Lc9
            r4.setVisible(r1)
        Lc9:
            java.lang.String r4 = "display_memory_information_recent_task"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.coui.appcompat.preference.COUISwitchPreference r4 = (com.coui.appcompat.preference.COUISwitchPreference) r4
            r3.E = r4
            if (r4 == 0) goto Ld8
            r4.setOnPreferenceChangeListener(r3)
        Ld8:
            java.lang.String r4 = "category_display_information_recent_task"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.coui.appcompat.preference.COUIPreferenceCategory r4 = (com.coui.appcompat.preference.COUIPreferenceCategory) r4
            r3.H = r4
            d36 r4 = new d36
            r4.<init>(r3)
            r3.R = r4
            r3.O = r1
            r3.P = r1
            java.lang.String r4 = "display_phone_manager_entrance"
            androidx.preference.Preference r4 = r3.findPreference(r4)
            com.coui.appcompat.preference.COUISwitchPreference r4 = (com.coui.appcompat.preference.COUISwitchPreference) r4
            r3.F = r4
            if (r4 == 0) goto L112
            wc8 r4 = defpackage.wc8.a
            boolean r4 = defpackage.im9.v()
            if (r4 != 0) goto L107
            com.coui.appcompat.preference.COUISwitchPreference r3 = r3.F
            r3.setVisible(r1)
            goto L112
        L107:
            boolean r4 = defpackage.im9.w()
            if (r4 == 0) goto L112
            com.coui.appcompat.preference.COUISwitchPreference r4 = r3.F
            r4.setOnPreferenceChangeListener(r3)
        L112:
            boolean r3 = defpackage.uy.O1()
            if (r3 == 0) goto L11c
            r3 = 20
            defpackage.t26.T = r3
        L11c:
            return
    }

    @Override // defpackage.vi1, defpackage.oj8
    public final void onCreatePreferences() {
            r3 = this;
            java.lang.String r0 = "onCreatePreferences"
            r1 = 0
            java.lang.String r2 = "LockSettingFragment"
            defpackage.q36.d(r1, r2, r0, r1)
            r0 = 2132148225(0x7f160001, float:1.9938422E38)
            r3.addPreferencesFromResource(r0)
            return
    }

    @Override // defpackage.bm0, defpackage.vi1, defpackage.oj8, androidx.fragment.app.n
    public final android.view.View onCreateView(android.view.LayoutInflater r3, android.view.ViewGroup r4, android.os.Bundle r5) {
            r2 = this;
            android.view.View r3 = super.onCreateView(r3, r4, r5)
            r4 = 2131362944(0x7f0a0480, float:1.8345683E38)
            android.view.View r4 = r3.findViewById(r4)
            android.widget.LinearLayout r4 = (android.widget.LinearLayout) r4
            r2.L = r4
            r5 = 0
            if (r4 == 0) goto L15
            r4.setVisibility(r5)
        L15:
            androidx.recyclerview.widget.RecyclerView r4 = r2.getListView()
            r2.M = r4
            r4.setVerticalScrollBarEnabled(r5)
            androidx.recyclerview.widget.RecyclerView r4 = r2.M
            o26 r0 = new o26
            r0.<init>()
            r4.setOnTouchListener(r0)
            android.content.Context r4 = r2.getContext()
            r2.C = r4
            if (r4 != 0) goto L32
            r2 = 0
            return r2
        L32:
            android.content.res.Resources r4 = r4.getResources()
            r0 = 2131165602(0x7f0701a2, float:1.7945426E38)
            float r4 = r4.getDimension(r0)
            int r4 = (int) r4
            android.graphics.Bitmap$Config r0 = android.graphics.Bitmap.Config.ALPHA_8
            android.graphics.Bitmap r4 = android.graphics.Bitmap.createBitmap(r4, r4, r0)
            android.graphics.drawable.BitmapDrawable r0 = new android.graphics.drawable.BitmapDrawable
            android.content.res.Resources r1 = r2.getResources()
            r0.<init>(r1, r4)
            r2.D = r0
            d36 r4 = r2.R
            android.content.Context r0 = r4.b
            zg6 r0 = defpackage.zg6.a(r0)
            t26 r4 = r4.c
            boolean r0 = r0.d
            com.coui.appcompat.preference.COUISwitchPreference r4 = r4.E
            if (r4 == 0) goto L62
            r4.setChecked(r0)
        L62:
            android.content.Context r4 = r2.C
            zg6 r4 = defpackage.zg6.a(r4)
            boolean r4 = r4.m
            if (r4 != 0) goto L8c
            com.coui.appcompat.preference.COUISwitchPreference r0 = r2.E
            if (r0 == 0) goto L8c
            r0.setVisible(r5)
            com.coui.appcompat.preference.COUISwitchPreference r5 = r2.E
            boolean r5 = r5.isVisible()
            if (r5 != 0) goto L8c
            com.coui.appcompat.preference.COUISwitchPreference r5 = r2.F
            boolean r5 = r5.isVisible()
            if (r5 != 0) goto L8c
            androidx.preference.PreferenceScreen r5 = r2.getPreferenceScreen()
            com.coui.appcompat.preference.COUIPreferenceCategory r0 = r2.H
            r5.j(r0)
        L8c:
            android.widget.LinearLayout r5 = r2.L
            if (r5 == 0) goto Le4
            boolean r5 = defpackage.zq7.c()
            if (r5 == 0) goto Le4
            wc8 r5 = defpackage.wc8.a
            boolean r5 = defpackage.im9.w()
            android.content.Context r0 = r2.C
            android.content.res.Resources r0 = r0.getResources()
            r1 = 2131169868(0x7f07124c, float:1.7954078E38)
            int r0 = r0.getDimensionPixelSize(r1)
            if (r4 == 0) goto Lad
            if (r5 == 0) goto Lb1
        Lad:
            if (r4 != 0) goto Lbe
            if (r5 == 0) goto Lbe
        Lb1:
            android.content.Context r0 = r2.C
            android.content.res.Resources r0 = r0.getResources()
            r1 = 2131169869(0x7f07124d, float:1.795408E38)
            int r0 = r0.getDimensionPixelSize(r1)
        Lbe:
            if (r4 == 0) goto Lcf
            if (r5 == 0) goto Lcf
            android.content.Context r4 = r2.C
            android.content.res.Resources r4 = r4.getResources()
            r5 = 2131169867(0x7f07124b, float:1.7954076E38)
            int r0 = r4.getDimensionPixelSize(r5)
        Lcf:
            android.widget.LinearLayout r4 = r2.L
            int r5 = r4.getPaddingLeft()
            android.widget.LinearLayout r1 = r2.L
            int r1 = r1.getPaddingRight()
            android.widget.LinearLayout r2 = r2.L
            int r2 = r2.getPaddingBottom()
            r4.setPadding(r5, r0, r1, r2)
        Le4:
            return r3
    }

    @Override // androidx.fragment.app.n
    public final void onDestroy() {
            r6 = this;
            super.onDestroy()
            d36 r0 = r6.R
            r0.getClass()
            o66 r1 = defpackage.o66.a
            c36 r2 = r0.e
            r1.removeListener(r2)
            z26 r0 = r0.d
            java.util.concurrent.ConcurrentLinkedDeque r1 = r0.h
            java.util.Iterator r2 = r1.iterator()
        L17:
            boolean r3 = r2.hasNext()
            r4 = 0
            if (r3 == 0) goto L2f
            java.lang.Object r3 = r2.next()
            x26 r3 = (defpackage.x26) r3
            r1.remove(r3)
            android.os.Handler r5 = r0.b
            r5.removeCallbacks(r3)
            r3.c = r4
            goto L17
        L2f:
            com.coui.appcompat.preference.COUIPreferenceCategory r0 = r6.G
            if (r0 == 0) goto L43
            java.lang.String r1 = "preference_power_save_mode_notice"
            androidx.preference.Preference r0 = r0.h(r1)
            com.oplus.quickstep.locksetting.ui.NoticePreference r0 = (com.oplus.quickstep.locksetting.ui.NoticePreference) r0
            if (r0 == 0) goto L43
            zj8 r0 = r0.getPreferenceManager()
            r0.j = r4
        L43:
            com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference r6 = r6.J
            if (r6 == 0) goto L5c
            by8 r0 = r6.c
            if (r0 == 0) goto L5a
            android.content.Context r1 = r6.getContext()
            if (r1 == 0) goto L5a
            android.content.ContentResolver r1 = r1.getContentResolver()
            if (r1 == 0) goto L5a
            r1.unregisterContentObserver(r0)
        L5a:
            r6.c = r4
        L5c:
            return
    }

    @Override // defpackage.bm0, androidx.fragment.app.n
    public final void onResume() {
            r4 = this;
            super.onResume()
            d36 r0 = r4.R
            android.content.Context r1 = r0.b
            zg6 r1 = defpackage.zg6.a(r1)
            t26 r0 = r0.c
            boolean r1 = r1.d
            com.coui.appcompat.preference.COUISwitchPreference r0 = r0.E
            if (r0 == 0) goto L16
            r0.setChecked(r1)
        L16:
            d36 r0 = r4.R
            r0.getClass()
            wc8 r1 = defpackage.wc8.a
            boolean r1 = defpackage.im9.w()
            r2 = 0
            java.lang.String r3 = "LockSettingPresenter"
            if (r1 != 0) goto L2d
            java.lang.String r0 = "setPhoneManagerEntranceSwitch -- feature has closed, not support phoneManager entrance"
            defpackage.q36.d(r2, r3, r0, r2)
            goto L4a
        L2d:
            android.content.Context r1 = r0.b
            defpackage.im9.l(r1)
            t26 r0 = r0.c
            boolean r1 = defpackage.wc8.d
            com.coui.appcompat.preference.COUISwitchPreference r0 = r0.F
            if (r0 == 0) goto L3d
            r0.setChecked(r1)
        L3d:
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            java.lang.String r1 = "setPhoneManagerEntranceSwitch--isSupport:"
            r0.<init>(r1)
            boolean r1 = defpackage.wc8.d
            defpackage.uw.g(r0, r1, r2, r3, r2)
        L4a:
            d36 r4 = r4.R
            r4.c()
            return
    }
}
