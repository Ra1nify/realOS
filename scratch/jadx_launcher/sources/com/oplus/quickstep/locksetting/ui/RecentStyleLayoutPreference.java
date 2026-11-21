package com.oplus.quickstep.locksetting.ui;

/* JADX INFO: compiled from: r8-map-id-f071591cd7900d703f8f4e591da4616fbd071031ee1e78187301dacbe5cf29c4 */
/* JADX INFO: loaded from: classes3.dex */
@kotlin.Metadata(d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u001b\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0002\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004¢\u0006\u0004\b\u0006\u0010\u0007¨\u0006\b"}, d2 = {"Lcom/oplus/quickstep/locksetting/ui/RecentStyleLayoutPreference;", "Landroidx/preference/Preference;", "Landroid/content/Context;", "context", "Landroid/util/AttributeSet;", "attrs", "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "OplusLauncher_RealmePallDomesticAallXxhdpiRelease"}, k = 1, mv = {2, 2, 0}, xi = 48)
public final class RecentStyleLayoutPreference extends androidx.preference.Preference {
    public defpackage.by8 c;
    public android.widget.LinearLayout q;
    public android.widget.LinearLayout r;
    public com.airbnb.lottie.LottieAnimationView s;
    public com.airbnb.lottie.LottieAnimationView t;
    public android.widget.TextView u;
    public android.widget.TextView v;
    public android.widget.RadioButton w;
    public android.widget.RadioButton x;

    public RecentStyleLayoutPreference(android.content.Context r1, android.util.AttributeSet r2) {
            r0 = this;
            r0.<init>(r1, r2)
            r1 = 2131559032(0x7f0d0278, float:1.8743397E38)
            r0.setLayoutResource(r1)
            return
    }

    public final boolean g() {
            r1 = this;
            android.content.Context r1 = r1.getContext()
            r1.getClass()
            r1.getClass()
            android.content.res.Resources r1 = r1.getResources()
            android.content.res.Configuration r1 = r1.getConfiguration()
            r1.getClass()
            int r1 = r1.uiMode
            r1 = r1 & 48
            r0 = 32
            if (r1 != r0) goto L1f
            r1 = 1
            goto L20
        L1f:
            r1 = 0
        L20:
            return r1
    }

    public final void h(java.lang.String r6) {
            r5 = this;
            java.lang.String r0 = "stacked"
            boolean r0 = kotlin.jvm.internal.Intrinsics.areEqual(r6, r0)
            r1 = 2131100829(0x7f06049d, float:1.781405E38)
            r2 = 2130969306(0x7f0402da, float:1.754729E38)
            r3 = 1
            r4 = 0
            if (r0 == 0) goto L3e
            android.widget.RadioButton r6 = r5.w
            if (r6 == 0) goto L18
            r6.setChecked(r3)
        L18:
            android.widget.RadioButton r6 = r5.x
            if (r6 == 0) goto L1f
            r6.setChecked(r4)
        L1f:
            android.widget.TextView r6 = r5.u
            if (r6 == 0) goto L2e
            android.content.Context r0 = r5.getContext()
            int r0 = defpackage.kb0.C(r0, r2, r4)
            r6.setTextColor(r0)
        L2e:
            android.widget.TextView r6 = r5.v
            if (r6 == 0) goto L73
            android.content.Context r5 = r5.getContext()
            int r5 = r5.getColor(r1)
            r6.setTextColor(r5)
            return
        L3e:
            java.lang.String r0 = "tiled"
            boolean r6 = kotlin.jvm.internal.Intrinsics.areEqual(r6, r0)
            if (r6 == 0) goto L73
            android.widget.RadioButton r6 = r5.x
            if (r6 == 0) goto L4e
            r6.setChecked(r3)
        L4e:
            android.widget.RadioButton r6 = r5.w
            if (r6 == 0) goto L55
            r6.setChecked(r4)
        L55:
            android.widget.TextView r6 = r5.v
            if (r6 == 0) goto L64
            android.content.Context r0 = r5.getContext()
            int r0 = defpackage.kb0.C(r0, r2, r4)
            r6.setTextColor(r0)
        L64:
            android.widget.TextView r6 = r5.u
            if (r6 == 0) goto L73
            android.content.Context r5 = r5.getContext()
            int r5 = r5.getColor(r1)
            r6.setTextColor(r5)
        L73:
            return
    }

    @Override // androidx.preference.Preference
    public final void onBindViewHolder(defpackage.ck8 r4) {
            r3 = this;
            super.onBindViewHolder(r4)
            r0 = 2131362890(0x7f0a044a, float:1.8345573E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof android.widget.LinearLayout
            r2 = 0
            if (r1 == 0) goto L12
            android.widget.LinearLayout r0 = (android.widget.LinearLayout) r0
            goto L13
        L12:
            r0 = r2
        L13:
            r3.q = r0
            r0 = 2131362891(0x7f0a044b, float:1.8345575E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof android.widget.LinearLayout
            if (r1 == 0) goto L23
            android.widget.LinearLayout r0 = (android.widget.LinearLayout) r0
            goto L24
        L23:
            r0 = r2
        L24:
            r3.r = r0
            boolean r0 = r3.g()
            if (r0 == 0) goto L3c
            r0 = 2131362782(0x7f0a03de, float:1.8345354E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof com.airbnb.lottie.LottieAnimationView
            if (r1 == 0) goto L3a
            com.airbnb.lottie.LottieAnimationView r0 = (com.airbnb.lottie.LottieAnimationView) r0
            goto L49
        L3a:
            r0 = r2
            goto L49
        L3c:
            r0 = 2131362781(0x7f0a03dd, float:1.8345352E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof com.airbnb.lottie.LottieAnimationView
            if (r1 == 0) goto L3a
            com.airbnb.lottie.LottieAnimationView r0 = (com.airbnb.lottie.LottieAnimationView) r0
        L49:
            r3.s = r0
            boolean r0 = r3.g()
            if (r0 == 0) goto L61
            r0 = 2131362784(0x7f0a03e0, float:1.8345358E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof com.airbnb.lottie.LottieAnimationView
            if (r1 == 0) goto L5f
            com.airbnb.lottie.LottieAnimationView r0 = (com.airbnb.lottie.LottieAnimationView) r0
            goto L6e
        L5f:
            r0 = r2
            goto L6e
        L61:
            r0 = 2131362783(0x7f0a03df, float:1.8345356E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof com.airbnb.lottie.LottieAnimationView
            if (r1 == 0) goto L5f
            com.airbnb.lottie.LottieAnimationView r0 = (com.airbnb.lottie.LottieAnimationView) r0
        L6e:
            r3.t = r0
            r0 = 2131363739(0x7f0a079b, float:1.8347295E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof android.widget.TextView
            if (r1 == 0) goto L7e
            android.widget.TextView r0 = (android.widget.TextView) r0
            goto L7f
        L7e:
            r0 = r2
        L7f:
            r3.u = r0
            r0 = 2131363740(0x7f0a079c, float:1.8347297E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof android.widget.TextView
            if (r1 == 0) goto L8f
            android.widget.TextView r0 = (android.widget.TextView) r0
            goto L90
        L8f:
            r0 = r2
        L90:
            r3.v = r0
            r0 = 2131362217(0x7f0a01a9, float:1.8344208E38)
            android.view.View r0 = r4.a(r0)
            boolean r1 = r0 instanceof android.widget.RadioButton
            if (r1 == 0) goto La0
            android.widget.RadioButton r0 = (android.widget.RadioButton) r0
            goto La1
        La0:
            r0 = r2
        La1:
            r3.w = r0
            r0 = 2131362220(0x7f0a01ac, float:1.8344214E38)
            android.view.View r4 = r4.a(r0)
            boolean r0 = r4 instanceof android.widget.RadioButton
            if (r0 == 0) goto Lb1
            r2 = r4
            android.widget.RadioButton r2 = (android.widget.RadioButton) r2
        Lb1:
            r3.x = r2
            com.airbnb.lottie.LottieAnimationView r4 = r3.s
            r0 = 0
            if (r4 == 0) goto Lbb
            r4.setVisibility(r0)
        Lbb:
            com.airbnb.lottie.LottieAnimationView r4 = r3.t
            if (r4 == 0) goto Lc2
            r4.setVisibility(r0)
        Lc2:
            android.content.Context r4 = r3.getContext()
            r4.getClass()
            android.content.ContentResolver r0 = r4.getContentResolver()
            java.lang.String r1 = "key_recent_style"
            java.lang.String r0 = android.provider.Settings.Secure.getString(r0, r1)
            if (r0 != 0) goto Lee
            boolean r0 = defpackage.op5.C
            if (r0 != 0) goto Le4
            boolean r0 = defpackage.zq7.b()
            if (r0 == 0) goto Le0
            goto Le4
        Le0:
            java.lang.String r0 = "stacked"
            goto Le7
        Le4:
            java.lang.String r0 = "tiled"
        Le7:
            android.content.ContentResolver r4 = r4.getContentResolver()
            android.provider.Settings.Secure.putString(r4, r1, r0)
        Lee:
            r3.h(r0)
            android.widget.LinearLayout r4 = r3.q
            if (r4 == 0) goto L102
            zx8 r0 = new zx8
            r0.<init>()
            r0.c = r3
            java.lang.invoke.VarHandle.storeStoreFence()
            r4.setOnClickListener(r0)
        L102:
            android.widget.LinearLayout r4 = r3.r
            if (r4 == 0) goto L113
            ay8 r0 = new ay8
            r0.<init>()
            r0.c = r3
            java.lang.invoke.VarHandle.storeStoreFence()
            r4.setOnClickListener(r0)
        L113:
            return
    }
}
