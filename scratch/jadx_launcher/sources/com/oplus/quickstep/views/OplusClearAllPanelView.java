package com.oplus.quickstep.views;

/* JADX INFO: compiled from: r8-map-id-f071591cd7900d703f8f4e591da4616fbd071031ee1e78187301dacbe5cf29c4 */
/* JADX INFO: loaded from: classes.dex */
public class OplusClearAllPanelView extends android.widget.LinearLayout implements defpackage.ej4 {
    public static final defpackage.vb7 R = null;
    public android.animation.ValueAnimator A;
    public float B;
    public float C;
    public float D;
    public float E;
    public float F;
    public java.lang.String G;
    public int H;
    public float I;
    public java.lang.String J;
    public int K;
    public boolean L;
    public com.android.launcher3.states.OPlusBaseState M;
    public boolean N;
    public boolean O;
    public defpackage.kw3 P;
    public com.android.launcher3.statemanager.StatefulActivity Q;
    public com.android.launcher.views.PressFeedbackButton c;
    public android.widget.FrameLayout.LayoutParams q;
    public com.oplus.quickstep.views.OplusCleanStorageView r;
    public android.animation.Animator s;
    public android.widget.TextView t;
    public android.animation.Animator u;
    public com.android.launcher3.LauncherState v;
    public int w;
    public defpackage.mc x;
    public boolean y;
    public final java.text.DecimalFormat z;

    static {
            vb7 r0 = new vb7
            java.lang.String r1 = "visibilityAlpha"
            r0.<init>(r1)
            com.oplus.quickstep.views.OplusClearAllPanelView.R = r0
            return
    }

    public OplusClearAllPanelView(android.content.Context r1, android.util.AttributeSet r2) {
            r0 = this;
            r0.<init>(r1, r2)
            r1 = 1
            r0.y = r1
            r1 = 0
            r0.B = r1
            r0.C = r1
            r0.D = r1
            r0.E = r1
            r0.F = r1
            r0.I = r1
            r1 = 0
            r0.L = r1
            r0.N = r1
            r0.O = r1
            java.text.DecimalFormat r1 = new java.text.DecimalFormat
            java.lang.String r2 = "0.00"
            r1.<init>(r2)
            r0.z = r1
            return
    }

    public static /* bridge */ /* synthetic */ defpackage.d39 a(com.oplus.quickstep.views.OplusClearAllPanelView r0) {
            d39 r0 = r0.getRecentsView()
            return r0
    }

    public static void g() {
            com.android.launcher3.util.ActivityTracker<com.android.launcher3.Launcher> r0 = com.android.launcher3.Launcher.ACTIVITY_TRACKER
            com.android.launcher3.BaseActivity r0 = r0.getCreatedActivity()
            boolean r1 = r0 instanceof com.android.launcher.Launcher
            if (r1 == 0) goto L49
            java.lang.String r1 = "OplusClearAllPanelView"
            java.lang.String r2 = "clear all app, destroyBrowser"
            r3 = 0
            defpackage.q36.d(r3, r1, r2, r3)
            com.android.launcher.Launcher r0 = (com.android.launcher.Launcher) r0
            com.android.systemui.plugins.shared.LauncherOverlayManager r0 = r0.getBrowserOverlayManager()
            uw0 r0 = (defpackage.uw0) r0
            r0.getClass()
            boolean r1 = defpackage.q36.s()
            if (r1 == 0) goto L32
            nw0 r1 = r0.c
            if (r1 == 0) goto L29
            r1 = 1
            goto L2a
        L29:
            r1 = 0
        L2a:
            java.lang.String r2 = "onDestroyed mClient not null:"
            java.lang.String r4 = "BrowserLauncherClient-BrowserLauncherCycleCallbacksImp"
            defpackage.e.i(r2, r1, r3, r4, r3)
        L32:
            nw0 r0 = r0.c
            if (r0 == 0) goto L49
            boolean r1 = defpackage.q36.s()
            if (r1 == 0) goto L44
            java.lang.String r1 = "BrowserLauncherClient"
            java.lang.String r2 = "onDestroyedByClearButton"
            defpackage.q36.d(r3, r1, r2, r3)
        L44:
            mi4 r0 = r0.a
            r0.c()
        L49:
            return
    }

    private defpackage.d39 getRecentsView() {
            r1 = this;
            android.view.ViewParent r1 = r1.getParent()
            if (r1 == 0) goto L11
            boolean r0 = r1 instanceof com.android.launcher3.RecentContainerView
            if (r0 == 0) goto L11
            com.android.launcher3.RecentContainerView r1 = (com.android.launcher3.RecentContainerView) r1
            com.android.quickstep.views.LauncherRecentsView r1 = r1.getMRecentView()
            return r1
        L11:
            r1 = 0
            return r1
    }

    public final void b() {
            r1 = this;
            android.animation.Animator r0 = r1.u
            if (r0 == 0) goto Lf
            boolean r0 = r0.isStarted()
            if (r0 == 0) goto Lf
            android.animation.Animator r1 = r1.u
            r1.cancel()
        Lf:
            return
    }

    public final void d(com.android.launcher3.LauncherState r1) {
            r0 = this;
            com.android.launcher3.LauncherState r0 = com.android.launcher3.LauncherState.NORMAL
            return
    }

    @Override // android.view.ViewGroup, android.view.View
    public final void dispatchDraw(android.graphics.Canvas r4) {
            r3 = this;
            super.dispatchDraw(r4)
            com.android.launcher.views.PressFeedbackButton r0 = r3.c
            if (r0 == 0) goto L12
            com.android.launcher3.util.ActivityTracker<com.android.launcher3.Launcher> r0 = com.android.launcher3.Launcher.ACTIVITY_TRACKER
            com.android.launcher3.BaseActivity r0 = r0.getCreatedActivity()
            r1 = 0
            r2 = 0
            r3.drawFocusFrame(r4, r0, r1, r2)
        L12:
            return
    }

    @Override // android.view.ViewGroup, android.view.View
    public final boolean dispatchHoverEvent(android.view.MotionEvent r4) {
            r3 = this;
            com.android.launcher3.statemanager.StatefulActivity r0 = r3.Q
            if (r0 != 0) goto Le
            android.content.Context r0 = r3.mContext
            com.android.launcher3.BaseActivity r0 = com.android.launcher3.BaseActivity.fromContext(r0)
            com.android.launcher3.statemanager.StatefulActivity r0 = (com.android.launcher3.statemanager.StatefulActivity) r0
            r3.Q = r0
        Le:
            d39 r0 = r3.getRecentsView()
            r1 = 1
            if (r0 == 0) goto L27
            d39 r0 = r3.getRecentsView()
            boolean r2 = r0 instanceof defpackage.zo7
            if (r2 == 0) goto L27
            zo7 r0 = (defpackage.zo7) r0
            boolean r0 = r0.Y3()
            if (r0 == 0) goto L27
            r0 = r1
            goto L28
        L27:
            r0 = 0
        L28:
            boolean r2 = r3.j()
            if (r2 != 0) goto L6f
            if (r0 == 0) goto L31
            goto L6f
        L31:
            kw3 r0 = r3.P
            if (r0 == 0) goto L6a
            com.android.launcher.views.PressFeedbackButton r0 = r3.c
            if (r0 == 0) goto L6a
            boolean r0 = defpackage.uy.O1()
            if (r0 == 0) goto L6a
            android.content.Context r0 = r3.mContext
            boolean r0 = com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled(r0)
            if (r0 != 0) goto L6a
            com.android.launcher3.statemanager.StatefulActivity r0 = r3.Q
            com.android.launcher3.statemanager.StateManager r0 = r0.getStateManager()
            if (r0 == 0) goto L6a
            com.android.launcher3.statemanager.StatefulActivity r0 = r3.Q
            com.android.launcher3.statemanager.StateManager r0 = r0.getStateManager()
            com.android.launcher3.statemanager.BaseState r0 = r0.getState()
            com.android.launcher3.uioverrides.states.OverviewState r1 = com.android.launcher3.LauncherState.OVERVIEW
            if (r0 == r1) goto L63
            com.android.launcher3.states.OPlusBaseState r0 = com.android.launcher3.states.OPlusBaseState.getTargetLauncherState()
            if (r0 != r1) goto L6a
        L63:
            kw3 r3 = r3.P
            boolean r3 = r3.handleHoverEvent(r4)
            return r3
        L6a:
            boolean r3 = super.dispatchHoverEvent(r4)
            return r3
        L6f:
            return r1
    }

    @Override // android.view.ViewGroup, android.view.View
    public final boolean dispatchKeyEvent(android.view.KeyEvent r3) {
            r2 = this;
            boolean r0 = defpackage.uy.O1()
            if (r0 == 0) goto L3b
            android.content.Context r0 = r2.mContext
            boolean r0 = com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled(r0)
            if (r0 == 0) goto Lf
            goto L3b
        Lf:
            android.view.ViewParent r0 = r2.getParent()
            boolean r1 = r0 instanceof com.android.launcher3.RecentContainerView
            if (r1 == 0) goto L36
            com.android.launcher3.RecentContainerView r0 = (com.android.launcher3.RecentContainerView) r0
            com.android.launcher.Launcher r1 = r0.getMLauncher()
            boolean r1 = r1.v0
            if (r1 == 0) goto L36
            int r1 = r3.getAction()
            if (r1 != 0) goto L36
            com.android.quickstep.views.LauncherRecentsView r1 = r0.getMRecentView()
            if (r1 == 0) goto L36
            com.android.quickstep.views.LauncherRecentsView r2 = r0.getMRecentView()
            boolean r2 = r2.dispatchKeyEvent(r3)
            return r2
        L36:
            boolean r2 = super.dispatchKeyEvent(r3)
            return r2
        L3b:
            boolean r2 = super.dispatchKeyEvent(r3)
            return r2
    }

    @Override // defpackage.ej4
    public final void drawShadow(android.graphics.Canvas r1, android.content.Context r2) {
            r0 = this;
            return
    }

    public final void e() {
            r4 = this;
            boolean r0 = r4.j()
            boolean r1 = defpackage.q36.s()
            if (r1 == 0) goto L12
            java.lang.String r1 = "clearEnterAnim: "
            r2 = 0
            java.lang.String r3 = "OplusClearAllPanelView"
            defpackage.e.i(r1, r0, r2, r3, r2)
        L12:
            if (r0 == 0) goto L1b
            android.animation.Animator r4 = r4.u
            if (r4 == 0) goto L1b
            r4.end()
        L1b:
            return
    }

    public int getBottomMargin() {
            r0 = this;
            int r0 = r0.w
            return r0
    }

    public int getClearButtonHeight() {
            r0 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r0.c
            if (r0 != 0) goto L6
            r0 = 0
            return r0
        L6:
            int r0 = r0.getMeasuredHeight()
            return r0
    }

    public android.view.View getClearPressButton() {
            r0 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r0.c
            return r0
    }

    @Override // defpackage.ej4
    public defpackage.kw3 getFocusFrameAnimManager() {
            r0 = this;
            kw3 r0 = r0.P
            return r0
    }

    @Override // defpackage.ej4
    public /* bridge */ /* synthetic */ float getHoverOriginScale() {
            r0 = this;
            r0 = 1065353216(0x3f800000, float:1.0)
            return r0
    }

    @Override // defpackage.ej4
    public float getHoverScale() {
            r0 = this;
            r0 = 1067030938(0x3f99999a, float:1.2)
            return r0
    }

    @Override // defpackage.ej4
    public android.view.View getHoverScaleView() {
            r0 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r0.c
            return r0
    }

    public boolean getIsGesture() {
            r1 = this;
            android.content.Context r1 = r1.getContext()
            com.android.launcher3.util.DisplayController$NavigationMode r1 = com.android.launcher3.util.DisplayController.getNavigationMode(r1)
            com.android.launcher3.util.DisplayController$NavigationMode r0 = com.android.launcher3.util.DisplayController.NavigationMode.NO_BUTTON
            if (r1 != r0) goto Le
            r1 = 1
            return r1
        Le:
            r1 = 0
            return r1
    }

    public boolean getIsMem() {
            r0 = this;
            android.content.Context r0 = r0.mContext
            zg6 r0 = defpackage.zg6.a(r0)
            boolean r0 = r0.d
            return r0
    }

    @Override // defpackage.ej4
    public android.graphics.RectF getItemBackgroundRect() {
            r0 = this;
            android.graphics.RectF r0 = r0.getItemFocusOrHoverRect()
            return r0
    }

    @Override // defpackage.ej4
    public float getItemFocusOrHoverRadius() {
            r2 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r2.c
            float r0 = r0.getMDrawableRadius()
            kw3 r1 = r2.P
            if (r1 == 0) goto L18
            boolean r2 = r2.O
            if (r2 == 0) goto L18
            float r2 = r1.getIsHoveringScale()
            r1 = 0
            int r1 = (r2 > r1 ? 1 : (r2 == r1 ? 0 : -1))
            if (r1 <= 0) goto L18
            float r0 = r0 * r2
        L18:
            return r0
    }

    @Override // defpackage.ej4
    public android.graphics.RectF getItemFocusOrHoverRect() {
            r6 = this;
            kw3 r0 = r6.P
            if (r0 == 0) goto L9
            float r0 = r0.getMaxFocusFrameStrokeWidth()
            goto La
        L9:
            r0 = 0
        La:
            kw3 r1 = r6.P
            if (r1 == 0) goto L49
            boolean r2 = r6.O
            if (r2 == 0) goto L49
            android.graphics.RectF r2 = new android.graphics.RectF
            com.android.launcher.views.PressFeedbackButton r3 = r6.c
            int r3 = r3.getLeft()
            float r3 = (float) r3
            com.android.launcher.views.PressFeedbackButton r4 = r6.c
            int r4 = r4.getTop()
            float r4 = (float) r4
            com.android.launcher.views.PressFeedbackButton r5 = r6.c
            int r5 = r5.getRight()
            float r5 = (float) r5
            com.android.launcher.views.PressFeedbackButton r6 = r6.c
            int r6 = r6.getBottom()
            float r6 = (float) r6
            r2.<init>(r3, r4, r5, r6)
            android.graphics.RectF r6 = r1.getHoverAreaRectF(r2)
            android.graphics.RectF r1 = new android.graphics.RectF
            float r2 = r6.left
            float r2 = r2 + r0
            float r3 = r6.top
            float r3 = r3 + r0
            float r4 = r6.right
            float r4 = r4 - r0
            float r6 = r6.bottom
            float r6 = r6 - r0
            r1.<init>(r2, r3, r4, r6)
            return r1
        L49:
            android.graphics.RectF r1 = new android.graphics.RectF
            com.android.launcher.views.PressFeedbackButton r2 = r6.c
            int r2 = r2.getLeft()
            float r2 = (float) r2
            float r2 = r2 + r0
            com.android.launcher.views.PressFeedbackButton r3 = r6.c
            int r3 = r3.getTop()
            float r3 = (float) r3
            float r3 = r3 + r0
            com.android.launcher.views.PressFeedbackButton r4 = r6.c
            int r4 = r4.getRight()
            float r4 = (float) r4
            float r4 = r4 - r0
            com.android.launcher.views.PressFeedbackButton r6 = r6.c
            int r6 = r6.getBottom()
            float r6 = (float) r6
            float r6 = r6 - r0
            r1.<init>(r2, r3, r4, r6)
            return r1
    }

    @Override // defpackage.ej4
    public final int getItemFrameColor(android.content.Context r2) {
            r1 = this;
            boolean r1 = defpackage.e0c.t
            if (r1 == 0) goto L23
            r2.getClass()
            android.content.res.Resources r1 = r2.getResources()
            android.content.res.Configuration r1 = r1.getConfiguration()
            r1.getClass()
            int r1 = r1.uiMode
            r1 = r1 & 48
            r0 = 32
            if (r1 != r0) goto L1b
            goto L23
        L1b:
            r1 = 2131101531(0x7f06075b, float:1.7815474E38)
            int r1 = r2.getColor(r1)
            return r1
        L23:
            r1 = 2131101532(0x7f06075c, float:1.7815476E38)
            int r1 = r2.getColor(r1)
            return r1
    }

    @Override // defpackage.ej4
    public android.graphics.RectF getItemShadowRect() {
            r5 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r5.c
            int r0 = r0.getTop()
            float r0 = (float) r0
            com.android.launcher.views.PressFeedbackButton r1 = r5.c
            int r1 = r1.getLeft()
            float r1 = (float) r1
            com.android.launcher.views.PressFeedbackButton r2 = r5.c
            int r2 = r2.getLeft()
            com.android.launcher.views.PressFeedbackButton r3 = r5.c
            int r3 = r3.getWidth()
            int r3 = r3 + r2
            float r2 = (float) r3
            com.android.launcher.views.PressFeedbackButton r3 = r5.c
            int r3 = r3.getTop()
            com.android.launcher.views.PressFeedbackButton r4 = r5.c
            int r4 = r4.getHeight()
            int r4 = r4 + r3
            float r3 = (float) r4
            kw3 r4 = r5.P
            if (r4 == 0) goto L3c
            boolean r5 = r5.O
            if (r5 == 0) goto L3c
            android.graphics.RectF r5 = new android.graphics.RectF
            r5.<init>(r1, r0, r2, r3)
            android.graphics.RectF r5 = r4.getHoverAreaRectF(r5)
            return r5
        L3c:
            android.graphics.RectF r5 = new android.graphics.RectF
            r5.<init>(r1, r0, r2, r3)
            return r5
    }

    public int getMemInfoLineHeight() {
            r0 = this;
            android.widget.TextView r0 = r0.t
            int r0 = r0.getLineHeight()
            return r0
    }

    public final void h(android.graphics.Rect r1) {
            r0 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r0.c
            if (r0 == 0) goto L7
            r0.getBoundsOnScreen(r1)
        L7:
            return
    }

    @Override // defpackage.ej4
    public final boolean hasFocusFlag() {
            r0 = this;
            boolean r0 = r0.N
            return r0
    }

    @Override // defpackage.ej4
    public final boolean hasHoverFlag() {
            r0 = this;
            boolean r0 = r0.O
            return r0
    }

    public final int i(boolean r2) {
            r1 = this;
            int r0 = r1.K
            if (r0 == 0) goto L8
            if (r2 == 0) goto L7
            goto L8
        L7:
            return r0
        L8:
            android.content.Context r2 = r1.getContext()
            java.lang.String r0 = "window"
            java.lang.Object r2 = r2.getSystemService(r0)
            android.view.WindowManager r2 = (android.view.WindowManager) r2
            android.view.Display r2 = r2.getDefaultDisplay()
            int r2 = r2.getHeight()
            r1.K = r2
            return r2
    }

    @Override // defpackage.ej4
    public final boolean isFocusFrameOutOnItem() {
            r0 = this;
            r0 = 0
            return r0
    }

    public final boolean j() {
            r1 = this;
            android.animation.Animator r0 = r1.u
            if (r0 == 0) goto L14
            boolean r0 = r0.isRunning()
            if (r0 != 0) goto L12
            android.animation.Animator r1 = r1.u
            boolean r1 = r1.isStarted()
            if (r1 == 0) goto L14
        L12:
            r1 = 1
            return r1
        L14:
            r1 = 0
            return r1
    }

    public final void k(com.android.launcher.Launcher r2, android.content.Intent r3) {
            r1 = this;
            if (r2 == 0) goto L7
            android.app.ActivityOptions r2 = r2.getResidentActivityOptions()
            goto Lb
        L7:
            android.app.ActivityOptions r2 = android.app.ActivityOptions.makeBasic()
        Lb:
            android.content.Context r1 = r1.mContext
            android.os.Bundle r2 = r2.toBundle()
            r1.startActivity(r3, r2)
            e01 r1 = defpackage.e01.b()
            aga r1 = r1.b
            r2 = 0
            r3 = 1
            java.lang.String r0 = "clear_button_long_click"
            r1.d(r0, r2, r3)
            return
    }

    public final void l() {
            r5 = this;
            r0 = 0
            java.lang.String r1 = "OplusClearAllPanelView"
            java.lang.String r2 = "----- longClick enter recent manager"
            defpackage.q36.d(r0, r1, r2, r0)
            com.android.launcher.views.PressFeedbackButton r1 = r5.c
            if (r1 == 0) goto L13
            ln1 r1 = r1.b0
            if (r1 == 0) goto L13
            r1.b()
        L13:
            android.content.Context r1 = r5.mContext
            int r2 = com.oplus.quickstep.locksetting.ui.LockSettingActivity.q
            android.content.Intent r2 = new android.content.Intent
            java.lang.Class<com.oplus.quickstep.locksetting.ui.LockSettingActivity> r3 = com.oplus.quickstep.locksetting.ui.LockSettingActivity.class
            r2.<init>(r1, r3)
            r1 = 270565376(0x10208000, float:3.1653044E-29)
            r2.addFlags(r1)
            android.content.Context r1 = r5.mContext
            com.android.launcher3.BaseActivity r1 = com.android.launcher3.BaseActivity.fromContext(r1)
            boolean r1 = r1 instanceof com.android.quickstep.RecentsActivity
            if (r1 == 0) goto L48
            com.android.launcher3.util.ActivityTracker r1 = com.android.quickstep.RecentsActivity.I
            com.android.launcher3.BaseActivity r1 = r1.getCreatedActivity()
            com.android.quickstep.RecentsActivity r1 = (com.android.quickstep.RecentsActivity) r1
            if (r1 == 0) goto L48
            r1.g()
            e01 r5 = defpackage.e01.b()
            aga r5 = r5.b
            java.lang.String r1 = "clear_button_long_click"
            r2 = 1
            r5.d(r1, r0, r2)
            return
        L48:
            android.content.Context r0 = r5.mContext
            int r1 = com.android.launcher.Launcher.N0
            com.android.launcher3.BaseActivity r0 = com.android.launcher3.BaseActivity.fromContext(r0)
            com.android.launcher.Launcher r0 = (com.android.launcher.Launcher) r0
            android.content.Context r1 = r5.mContext
            com.android.launcher3.BaseActivity r1 = com.android.launcher3.BaseActivity.fromContext(r1)
            com.android.launcher.Launcher r1 = (com.android.launcher.Launcher) r1
            android.view.View r1 = r1.getOverviewPanel()
            zo7 r1 = (defpackage.zo7) r1
            sb7 r3 = new sb7
            r4 = 0
            r3.<init>(r4)
            r3.q = r5
            r3.r = r0
            r3.s = r2
            java.lang.invoke.VarHandle.storeStoreFence()
            if (r1 == 0) goto L75
            r1.K3(r3)
            return
        L75:
            r3.run()
            return
    }

    public final void m() {
            r3 = this;
            android.content.Context r3 = r3.mContext
            java.lang.String r0 = defpackage.ob5.a
            com.oplus.dispatch.OCDExecutorService r0 = defpackage.md7.d()
            pw1 r1 = new pw1
            r2 = 1
            r1.<init>(r2)
            r1.q = r3
            java.lang.invoke.VarHandle.storeStoreFence()
            r0.submit(r1)
            g()
            return
    }

    public final void n(android.view.View.OnClickListener r5, android.view.View r6) {
            r4 = this;
            android.content.Context r0 = r4.mContext
            java.lang.String r1 = "com.oplus.athena"
            boolean r0 = com.android.launcher3.util.OplusMBAStartActivityHelper.checkAppAvailable(r0, r1)
            r2 = 0
            java.lang.String r3 = "OplusClearAllPanelView"
            if (r0 != 0) goto L3a
            java.lang.String r5 = "----- onClick clear all app"
            defpackage.q36.d(r2, r3, r5, r2)
            android.content.Context r5 = r4.mContext
            int r6 = com.android.launcher.Launcher.N0
            com.android.launcher3.BaseActivity r5 = com.android.launcher3.BaseActivity.fromContext(r5)
            com.android.launcher.Launcher r5 = (com.android.launcher.Launcher) r5
            mn2 r6 = new mn2
            r0 = 3
            r6.<init>(r0)
            r6.q = r4
            java.lang.invoke.VarHandle.storeStoreFence()
            nn2 r0 = new nn2
            r2 = 4
            r0.<init>(r2)
            r0.q = r4
            java.lang.invoke.VarHandle.storeStoreFence()
            r2 = 1
            mc r5 = com.android.launcher3.util.OplusMBAStartActivityHelper.showGrantDialog(r5, r1, r2, r6, r0)
            r4.x = r5
            return
        L3a:
            java.lang.String r4 = "----- else onClick clear all app"
            defpackage.q36.d(r2, r3, r4, r2)
            r5.onClick(r6)
            g()
            return
    }

    public final void o(defpackage.yg6 r9, boolean r10, defpackage.zg6 r11) {
            r8 = this;
            int r0 = r9.b
            java.lang.String r9 = r9.a
            r8.setTextDirection(r0)
            android.widget.TextView r0 = r8.t
            r1 = 0
            r0.setVisibility(r1)
            boolean r0 = defpackage.q36.s()
            r1 = 0
            java.lang.String r2 = "OplusClearAllPanelView"
            java.lang.String r3 = ""
            if (r0 == 0) goto L22
            java.lang.String r0 = "updateMeminfo: new memory is "
            java.lang.String r0 = r0.concat(r9)
            defpackage.q36.d(r3, r2, r0, r1)
        L22:
            if (r10 == 0) goto Lb7
            float r10 = r11.i
            r0 = 0
            int r0 = (r10 > r0 ? 1 : (r10 == r0 ? 0 : -1))
            if (r0 <= 0) goto Lb7
            java.lang.String r0 = r11.j
            java.lang.String r4 = r11.k
            boolean r5 = r11.l
            float r6 = r8.F
            r8.F = r10
            java.lang.String r10 = r8.G
            r8.G = r9
            android.animation.ValueAnimator r9 = r8.A
            if (r9 == 0) goto L81
            boolean r9 = r9.isRunning()
            if (r9 == 0) goto L81
            float r9 = r8.B
            r8.D = r9
            float r9 = r8.C
            r8.E = r9
            boolean r9 = defpackage.q36.s()
            if (r9 == 0) goto Lbc
            java.lang.StringBuilder r9 = new java.lang.StringBuilder
            java.lang.String r0 = "playMemoryChangeAnimation: mNewMemoryNumber = "
            r9.<init>(r0)
            float r0 = r8.F
            java.lang.String r4 = ", oldMemory = "
            java.lang.String r5 = ", mAnimationStartNumber = "
            defpackage.h7.d(r0, r6, r4, r5, r9)
            float r0 = r8.D
            r9.append(r0)
            java.lang.String r0 = ", mAnimationStartProgress = "
            r9.append(r0)
            float r0 = r8.E
            r9.append(r0)
            java.lang.String r0 = ", oldFinalMemoryStr = "
            r9.append(r0)
            r9.append(r10)
            java.lang.String r9 = r9.toString()
            defpackage.q36.d(r3, r2, r9, r1)
            goto Lbc
        L81:
            r9 = 2
            float[] r9 = new float[r9]
            r9 = {x0108: FILL_ARRAY_DATA , data: [0, 1065353216} // fill-array
            android.animation.ValueAnimator r9 = android.animation.ValueAnimator.ofFloat(r9)
            r8.A = r9
            r6 = 300(0x12c, double:1.48E-321)
            r9.setDuration(r6)
            android.animation.ValueAnimator r9 = r8.A
            ub7 r10 = new ub7
            r10.<init>()
            r10.c = r8
            r10.q = r0
            r10.r = r5
            r10.s = r4
            java.lang.invoke.VarHandle.storeStoreFence()
            r9.addUpdateListener(r10)
            android.animation.ValueAnimator r9 = r8.A
            xb7 r10 = new xb7
            r10.<init>(r8)
            r9.addListener(r10)
            android.animation.ValueAnimator r9 = r8.A
            r9.start()
            goto Lbc
        Lb7:
            android.widget.TextView r10 = r8.t
            r10.setText(r9)
        Lbc:
            java.lang.String r9 = r11.e
            java.lang.String r10 = r11.g
            long r4 = r11.c
            r6 = 0
            int r0 = (r4 > r6 ? 1 : (r4 == r6 ? 0 : -1))
            java.lang.String r11 = r11.f
            if (r0 != 0) goto Lcb
            goto Ld1
        Lcb:
            java.lang.String r0 = " + "
            java.lang.String r11 = defpackage.ep.a(r11, r0, r10)
        Ld1:
            java.lang.StringBuilder r10 = new java.lang.StringBuilder
            r10.<init>()
            android.content.Context r0 = r8.mContext
            r4 = 2131953324(0x7f1306ac, float:1.9543116E38)
            java.lang.String r0 = r0.getString(r4)
            r10.append(r0)
            java.lang.String r0 = ", "
            r10.append(r0)
            android.content.Context r0 = r8.mContext
            r4 = 2131953323(0x7f1306ab, float:1.9543114E38)
            java.lang.Object[] r9 = new java.lang.Object[]{r11, r9}
            java.lang.String r9 = r0.getString(r4, r9)
            r10.append(r9)
            java.lang.String r9 = r10.toString()
            java.lang.String r10 = "getMemoryClearButtonDescriptionString: "
            java.lang.String r10 = r10.concat(r9)
            defpackage.q36.d(r3, r2, r10, r1)
            r8.setContentDescription(r9)
            return
    }

    @Override // android.view.View
    public final void onConfigurationChanged(android.content.res.Configuration r6) {
            r5 = this;
            super.onConfigurationChanged(r6)
            r0 = 1
            r5.i(r0)
            com.android.launcher.views.PressFeedbackButton r1 = r5.c
            if (r1 != 0) goto Lc
            return
        Lc:
            android.view.ViewGroup$LayoutParams r1 = r1.getLayoutParams()
            android.widget.RelativeLayout$LayoutParams r1 = (android.widget.RelativeLayout.LayoutParams) r1
            android.content.pm.IPackageManager r2 = defpackage.f38.a
            java.util.ArrayList r2 = new java.util.ArrayList
            r2.<init>()
            android.content.ComponentName r2 = defpackage.f38.b(r2)
            int r6 = r6.orientation
            r3 = 0
            if (r6 != r0) goto L23
            goto L24
        L23:
            r0 = r3
        L24:
            if (r2 == 0) goto L52
            java.lang.String r6 = r2.getPackageName()
            java.lang.String r4 = "com.android.launcher"
            boolean r6 = android.text.TextUtils.equals(r6, r4)
            if (r6 != 0) goto L52
            java.lang.String r6 = r2.getPackageName()
            java.lang.String r2 = "com.coloros.bootreg"
            boolean r6 = android.text.TextUtils.equals(r6, r2)
            if (r6 != 0) goto L52
            if (r0 != 0) goto L52
            android.content.Context r6 = r5.mContext
            boolean r0 = r6 instanceof com.android.launcher.Launcher
            if (r0 == 0) goto L65
            android.content.res.Resources r6 = r6.getResources()
            r0 = 2131166052(0x7f070364, float:1.7946338E38)
            int r3 = r6.getDimensionPixelSize(r0)
            goto L65
        L52:
            boolean r6 = defpackage.d01.C()
            if (r6 != 0) goto L65
            android.content.Context r6 = r5.mContext
            android.content.res.Resources r6 = r6.getResources()
            r0 = 2131166053(0x7f070365, float:1.794634E38)
            int r3 = r6.getDimensionPixelSize(r0)
        L65:
            r1.topMargin = r3
            com.android.launcher.views.PressFeedbackButton r5 = r5.c
            r5.setLayoutParams(r1)
            return
    }

    @Override // android.view.ViewGroup, android.view.View
    public final void onDetachedFromWindow() {
            r1 = this;
            mc r0 = r1.x
            if (r0 == 0) goto La
            r0.dismiss()
            r0 = 0
            r1.x = r0
        La:
            super.onDetachedFromWindow()
            return
    }

    @Override // android.view.View
    public final void onFinishInflate() {
            r2 = this;
            super.onFinishInflate()
            r0 = 2131362090(0x7f0a012a, float:1.834395E38)
            android.view.View r0 = r2.findViewById(r0)
            com.android.launcher.views.PressFeedbackButton r0 = (com.android.launcher.views.PressFeedbackButton) r0
            r2.c = r0
            android.content.Context r0 = r2.getContext()
            com.android.launcher.views.PressFeedbackButton r1 = r2.c
            java.lang.CharSequence r1 = r1.getText()
            java.lang.String r1 = r1.toString()
            defpackage.q36.y(r0, r1)
            r0 = 2131363849(0x7f0a0809, float:1.8347518E38)
            android.view.View r0 = r2.findViewById(r0)
            android.widget.TextView r0 = (android.widget.TextView) r0
            r2.t = r0
            android.view.ViewGroup$LayoutParams r0 = r2.getLayoutParams()
            android.widget.FrameLayout$LayoutParams r0 = (android.widget.FrameLayout.LayoutParams) r0
            r2.q = r0
            r1 = 80
            r0.gravity = r1
            float r0 = r2.getTranslationZ()
            r2.I = r0
            r0 = 1
            r2.i(r0)
            r1 = 2131362230(0x7f0a01b6, float:1.8344235E38)
            android.view.View r1 = r2.findViewById(r1)
            com.oplus.quickstep.views.OplusCleanStorageView r1 = (com.oplus.quickstep.views.OplusCleanStorageView) r1
            r2.r = r1
            wc8 r1 = defpackage.wc8.a
            android.content.Context r1 = r2.mContext
            defpackage.im9.l(r1)
            android.content.Context r1 = defpackage.wc8.f
            if (r1 == 0) goto L5b
            android.content.Context r1 = r1.getApplicationContext()
            goto L5c
        L5b:
            r1 = 0
        L5c:
            defpackage.im9.x(r1)
            android.content.Context r1 = defpackage.wc8.f
            android.content.Intent r1 = defpackage.im9.d(r1)
            if (r1 == 0) goto L68
            goto L69
        L68:
            r0 = 0
        L69:
            defpackage.wc8.e = r0
            boolean r0 = defpackage.im9.v()
            if (r0 == 0) goto L93
            android.content.Context r0 = r2.mContext
            defpackage.im9.l(r0)
            com.oplus.quickstep.views.OplusCleanStorageView r0 = r2.r
            r0.getClass()
            uc8 r1 = new uc8
            r1.<init>()
            java.lang.invoke.VarHandle.storeStoreFence()
            r0.setOnClickListener(r1)
            vc8 r1 = new vc8
            r1.<init>()
            r1.c = r0
            java.lang.invoke.VarHandle.storeStoreFence()
            r0.setOnLongClickListener(r1)
        L93:
            com.android.launcher.views.PressFeedbackButton r0 = r2.c
            pb7 r1 = new pb7
            r1.<init>()
            r1.c = r2
            java.lang.invoke.VarHandle.storeStoreFence()
            r0.setOnLongClickListener(r1)
            kw3 r0 = new kw3
            r0.<init>(r2)
            r2.P = r0
            android.content.Context r0 = r2.mContext
            com.android.launcher3.BaseActivity r0 = com.android.launcher3.BaseActivity.fromContext(r0)
            com.android.launcher3.statemanager.StatefulActivity r0 = (com.android.launcher3.statemanager.StatefulActivity) r0
            r2.Q = r0
            return
    }

    @Override // android.view.View
    public final void onFocusChanged(boolean r3, int r4, android.graphics.Rect r5) {
            r2 = this;
            kw3 r0 = r2.P
            if (r0 == 0) goto L9
            r1 = r3 ^ 1
            r0.startFocusFrameAnim(r1)
        L9:
            super.onFocusChanged(r3, r4, r5)
            return
    }

    public final boolean p(int r3, android.graphics.Rect r4, android.graphics.Rect r5, int r6) {
            r2 = this;
            r0 = 0
            if (r5 == 0) goto L26
            boolean r1 = r5.isValid()
            if (r1 == 0) goto L26
            int r1 = r5.width()
            if (r1 == 0) goto L26
            int r1 = r5.height()
            if (r1 != 0) goto L16
            goto L26
        L16:
            int r4 = r4.bottom
            int r3 = r3 - r4
            int r4 = r5.bottom
            int r3 = r3 - r4
            if (r6 <= 0) goto L26
            int r2 = r2.H
            if (r2 == 0) goto L26
            if (r2 == r3) goto L26
            r2 = 1
            return r2
        L26:
            return r0
    }

    public final void q(boolean r2) {
            r1 = this;
            android.view.ViewParent r1 = r1.getParent()
            boolean r0 = r1 instanceof com.android.launcher3.RecentContainerView
            if (r0 == 0) goto L2a
            com.android.launcher3.RecentContainerView r1 = (com.android.launcher3.RecentContainerView) r1
            java.lang.String r0 = defpackage.ia2.a
            boolean r0 = com.oplus.quickstep.common.settingsvalue.a.d()
            if (r0 == 0) goto L2a
            com.android.quickstep.views.LauncherRecentsView r0 = r1.getMRecentView()
            if (r0 == 0) goto L2a
            com.android.quickstep.views.LauncherRecentsView r1 = r1.getMRecentView()
            kx8 r1 = r1.getRelayInteraction()
            if (r2 == 0) goto L25
            p99 r2 = defpackage.p99.i
            goto L27
        L25:
            o99 r2 = defpackage.o99.i
        L27:
            r1.c(r2)
        L2a:
            return
    }

    public final void r(int r5, android.graphics.Rect r6, android.graphics.Rect r7, boolean r8, com.oplus.quickstep.dock.DockView r9) {
            r4 = this;
            int r0 = r6.bottom
            int r0 = r5 - r0
            int r1 = r7.bottom
            int r0 = r0 - r1
            r4.H = r0
            if (r8 == 0) goto L1d
            boolean r8 = defpackage.zn9.j()
            if (r8 == 0) goto L18
            android.content.Context r8 = r4.mContext
            int r8 = defpackage.qn9.d(r8)
            int r0 = r0 + r8
        L18:
            int r8 = r0 / 2
            r4.w = r8
            goto L83
        L1d:
            float r8 = (float) r0
            boolean r1 = defpackage.zn9.k()
            if (r1 != 0) goto L2f
            boolean r1 = defpackage.zn9.x()
            if (r1 == 0) goto L2b
            goto L2f
        L2b:
            r1 = 1057803469(0x3f0ccccd, float:0.55)
            goto L32
        L2f:
            r1 = 1055286886(0x3ee66666, float:0.45)
        L32:
            float r8 = r8 * r1
            int r8 = (int) r8
            int r1 = r7.bottom
            int r2 = r8 / 2
            int r1 = r1 + r2
            int r2 = r8 - r2
            int r8 = r0 - r8
            int r8 = r8 + r2
            int r8 = r8 / 2
            r4.w = r8
            android.content.Context r8 = r4.mContext
            defpackage.zg6.a(r8)
            android.content.Context r8 = r4.getContext()
            com.android.launcher3.util.DisplayController$NavigationMode r8 = com.android.launcher3.util.DisplayController.getNavigationMode(r8)
            int r2 = r4.w
            com.android.launcher3.util.DisplayController$NavigationMode r3 = com.android.launcher3.util.DisplayController.NavigationMode.THREE_BUTTONS
            if (r8 != r3) goto L68
            android.content.Context r8 = r4.getContext()
            android.content.res.Resources r8 = r8.getResources()
            r3 = 2131171123(0x7f071733, float:1.7956624E38)
            int r8 = r8.getDimensionPixelSize(r3)
            int r2 = r2 - r8
            r4.w = r2
            goto L7a
        L68:
            android.content.Context r8 = r4.getContext()
            android.content.res.Resources r8 = r8.getResources()
            r3 = 2131168367(0x7f070c6f, float:1.7951034E38)
            int r8 = r8.getDimensionPixelSize(r3)
            int r8 = r8 + r2
            r4.w = r8
        L7a:
            if (r9 == 0) goto L83
            int r8 = r7.width()
            r9.i(r6, r1, r8)
        L83:
            java.lang.String r8 = " heightPx = "
            java.lang.String r9 = " insets.bottom = "
            java.lang.String r1 = "setInsets: bottomSpace = "
            java.lang.StringBuilder r5 = defpackage.z10.e(r0, r5, r1, r8, r9)
            int r6 = r6.bottom
            r5.append(r6)
            java.lang.String r6 = " mTempRect.bottom = "
            r5.append(r6)
            int r6 = r7.bottom
            r5.append(r6)
            java.lang.String r6 = " bottomMargin = "
            r5.append(r6)
            int r6 = r4.w
            r5.append(r6)
            java.lang.String r6 = " height() = "
            r5.append(r6)
            int r4 = r4.getHeight()
            r5.append(r4)
            java.lang.String r4 = " ,tempRect"
            r5.append(r4)
            r5.append(r7)
            java.lang.String r4 = r5.toString()
            r5 = 0
            java.lang.String r6 = ""
            java.lang.String r7 = "OplusClearAllPanelView"
            defpackage.q36.d(r6, r7, r4, r5)
            return
    }

    public final void s(com.android.launcher3.LauncherState r12, boolean r13, int r14, com.android.launcher3.states.StateAnimationConfig r15) {
            r11 = this;
            com.android.launcher3.states.OPlusBaseState r0 = com.android.launcher3.states.OPlusBaseState.getTargetLauncherState()
            com.android.launcher3.uioverrides.states.OverviewState r1 = com.android.launcher3.LauncherState.OVERVIEW
            r2 = 1
            r3 = 0
            if (r0 == r1) goto Lc
            r4 = r2
            goto Ld
        Lc:
            r4 = r3
        Ld:
            android.content.Context r5 = r11.mContext
            com.android.launcher3.BaseActivity r5 = com.android.launcher3.BaseActivity.fromContext(r5)
            com.android.launcher3.statemanager.StatefulActivity r5 = (com.android.launcher3.statemanager.StatefulActivity) r5
            if (r13 == 0) goto L31
            if (r4 != 0) goto L30
            com.android.launcher3.LauncherState r4 = r11.v
            if (r4 == 0) goto L2e
            if (r4 != r1) goto L2e
            com.android.launcher3.statemanager.StateManager r4 = r5.getStateManager()
            com.android.launcher3.statemanager.BaseState r4 = r4.getState()
            if (r4 != r0) goto L2e
            com.android.launcher3.states.OPlusBaseState r4 = r11.M
            if (r4 != r1) goto L2e
            goto L30
        L2e:
            r4 = r3
            goto L31
        L30:
            r4 = r2
        L31:
            r6 = 0
            if (r4 == 0) goto L68
            if (r0 != r1) goto L68
            if (r12 != r1) goto L68
            android.view.View r1 = r11.getClearPressButton()
            int[] r1 = r1.getLocationOnScreen()
            r7 = r1[r3]
            if (r7 < 0) goto L67
            android.content.Context r8 = r11.mContext
            int r8 = com.android.launcher3.taskbar.TaskbarUtils.getScreenWidth(r8)
            if (r7 > r8) goto L67
            r1 = r1[r2]
            if (r1 < 0) goto L67
            android.content.Context r7 = r11.mContext
            int r7 = com.android.launcher3.taskbar.TaskbarUtils.getScreenHeight(r7)
            if (r1 <= r7) goto L59
            goto L67
        L59:
            int r1 = r11.getVisibility()
            if (r1 != 0) goto L67
            float r1 = r11.getAlpha()
            int r1 = (r1 > r6 ? 1 : (r1 == r6 ? 0 : -1))
            if (r1 != 0) goto L68
        L67:
            r4 = r3
        L68:
            boolean r1 = defpackage.q36.u()
            java.lang.String r7 = "OplusClearAllPanelView"
            java.lang.String r8 = ""
            if (r1 == 0) goto Lbb
            java.lang.String r1 = ", rotation="
            java.lang.String r9 = ", needReturn="
            java.lang.String r10 = "playEnterAnimation(), check="
            java.lang.StringBuilder r13 = defpackage.f.f(r14, r10, r1, r9, r13)
            r13.append(r4)
            java.lang.String r1 = ", toState="
            r13.append(r1)
            r13.append(r12)
            java.lang.String r1 = ", mLastState="
            r13.append(r1)
            com.android.launcher3.LauncherState r1 = r11.v
            r13.append(r1)
            java.lang.String r1 = ", curState="
            r13.append(r1)
            com.android.launcher3.statemanager.StateManager r1 = r5.getStateManager()
            com.android.launcher3.statemanager.BaseState r1 = r1.getState()
            r13.append(r1)
            java.lang.String r1 = ", tragetState="
            r13.append(r1)
            r13.append(r0)
            java.lang.String r1 = ", mLastTargetState="
            r13.append(r1)
            com.android.launcher3.states.OPlusBaseState r1 = r11.M
            r13.append(r1)
            java.lang.String r13 = r13.toString()
            defpackage.q36.p(r8, r7, r13)
        Lbb:
            r11.v = r12
            r11.M = r0
            if (r4 != 0) goto L297
            com.android.launcher3.LauncherState r13 = com.android.launcher3.LauncherState.NORMAL
            if (r12 == r13) goto L297
            com.android.launcher3.uioverrides.states.OplusAllAppsState r13 = com.android.launcher3.LauncherState.ALL_APPS
            if (r12 != r13) goto Lcb
            goto L297
        Lcb:
            r11.d(r12)
            android.animation.Animator r12 = r11.s
            if (r12 == 0) goto Ldd
            boolean r12 = r12.isStarted()
            if (r12 == 0) goto Ldd
            android.animation.Animator r12 = r11.s
            r12.cancel()
        Ldd:
            r12 = 1065353216(0x3f800000, float:1.0)
            r11.setViewClickable(r12)
            r11.b()
            java.lang.String r12 = "prepareClearAllEnterIsolation done vis="
            java.lang.String r13 = "ClearAll#prepareEnterIsolation"
            android.os.Trace.beginSection(r13)
            int r13 = r11.getVisibility()     // Catch: java.lang.Throwable -> L292
            r0 = 0
            if (r13 == 0) goto L10e
            float r13 = r11.getAlpha()     // Catch: java.lang.Throwable -> L292
            int r13 = (r13 > r6 ? 1 : (r13 == r6 ? 0 : -1))
            if (r13 == 0) goto Lff
            r11.setAlpha(r6)     // Catch: java.lang.Throwable -> L292
        Lff:
            r11.setVisibility(r3)     // Catch: java.lang.Throwable -> L292
            boolean r13 = defpackage.q36.s()     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L10e
            java.lang.String r13 = "prepareClearAllEnterIsolation setVisibility(VISIBLE)"
            defpackage.q36.d(r8, r7, r13, r0)     // Catch: java.lang.Throwable -> L292
        L10e:
            int r13 = com.android.launcher3.uioverrides.states.blurdrawable.RenderEffectManager.c     // Catch: java.lang.Throwable -> L292
            boolean r13 = com.android.launcher3.uioverrides.states.blurdrawable.RenderEffectManager.Companion.isUiRenderEnable()     // Catch: java.lang.Throwable -> L292
            r1 = 2
            if (r13 != 0) goto L120
            int r13 = r11.getLayerType()     // Catch: java.lang.Throwable -> L292
            if (r13 == r1) goto L120
            r11.setLayerType(r1, r0)     // Catch: java.lang.Throwable -> L292
        L120:
            com.android.launcher.views.PressFeedbackButton r13 = r11.c     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L148
            boolean r13 = com.android.launcher3.uioverrides.states.blurdrawable.RenderEffectManager.Companion.isUiRenderEnable()     // Catch: java.lang.Throwable -> L292
            com.android.launcher.views.PressFeedbackButton r4 = r11.c
            if (r13 == 0) goto L138
            int r13 = r4.getLayerType()     // Catch: java.lang.Throwable -> L292
            if (r13 == r1) goto L143
            com.android.launcher.views.PressFeedbackButton r13 = r11.c     // Catch: java.lang.Throwable -> L292
            r13.setLayerType(r1, r0)     // Catch: java.lang.Throwable -> L292
            goto L143
        L138:
            int r13 = r4.getLayerType()     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L143
            com.android.launcher.views.PressFeedbackButton r13 = r11.c     // Catch: java.lang.Throwable -> L292
            r13.setLayerType(r3, r0)     // Catch: java.lang.Throwable -> L292
        L143:
            com.android.launcher.views.PressFeedbackButton r13 = r11.c     // Catch: java.lang.Throwable -> L292
            r13.setForceBlockDraw(r2)     // Catch: java.lang.Throwable -> L292
        L148:
            java.lang.String r13 = "onDragStart"
            java.lang.String r4 = r11.J     // Catch: java.lang.Throwable -> L292
            boolean r13 = r13.equals(r4)     // Catch: java.lang.Throwable -> L292
            if (r13 != 0) goto L174
            android.content.Context r13 = r11.mContext     // Catch: java.lang.Throwable -> L292
            com.android.launcher3.util.DisplayController$NavigationMode r13 = com.android.launcher3.util.DisplayController.getNavigationMode(r13)     // Catch: java.lang.Throwable -> L292
            com.android.launcher3.util.DisplayController$NavigationMode r4 = com.android.launcher3.util.DisplayController.NavigationMode.THREE_BUTTONS     // Catch: java.lang.Throwable -> L292
            if (r13 != r4) goto L16e
            java.lang.String r13 = "launchTaskAnimatedAsync"
            java.lang.String r4 = r11.J     // Catch: java.lang.Throwable -> L292
            boolean r13 = r13.equals(r4)     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L16e
            boolean r13 = defpackage.uy.I0()     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L174
        L16e:
            java.lang.String r13 = "playEnterAnimation#onAnimationStart"
            r11.u(r13, r2)     // Catch: java.lang.Throwable -> L292
        L174:
            boolean r13 = com.android.launcher3.util.LauncherMaterialEffectUtils.isBlurEnabled()     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L17f
            android.content.Context r13 = r11.mContext     // Catch: java.lang.Throwable -> L292
            com.android.launcher3.uioverrides.states.blurdrawable.BlurUtils.pauseBlurService(r13, r3)     // Catch: java.lang.Throwable -> L292
        L17f:
            boolean r13 = defpackage.q36.u()     // Catch: java.lang.Throwable -> L292
            if (r13 == 0) goto L1c2
            java.lang.StringBuilder r13 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L292
            r13.<init>(r12)     // Catch: java.lang.Throwable -> L292
            int r12 = r11.getVisibility()     // Catch: java.lang.Throwable -> L292
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            java.lang.String r12 = " alpha="
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            float r12 = r11.getAlpha()     // Catch: java.lang.Throwable -> L292
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            java.lang.String r12 = " panelLayer="
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            int r12 = r11.getLayerType()     // Catch: java.lang.Throwable -> L292
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            java.lang.String r12 = " btnLayer="
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            com.android.launcher.views.PressFeedbackButton r12 = r11.c     // Catch: java.lang.Throwable -> L292
            if (r12 == 0) goto L1b7
            int r12 = r12.getLayerType()     // Catch: java.lang.Throwable -> L292
            goto L1b8
        L1b7:
            r12 = -1
        L1b8:
            r13.append(r12)     // Catch: java.lang.Throwable -> L292
            java.lang.String r12 = r13.toString()     // Catch: java.lang.Throwable -> L292
            defpackage.q36.p(r0, r7, r12)     // Catch: java.lang.Throwable -> L292
        L1c2:
            android.os.Trace.endSection()
            vf1 r12 = com.android.launcher3.anim.light.RecentsLightAnimUtil.INTERPOLATOR_MOVE_EASE
            boolean r12 = defpackage.ei.g()
            if (r12 != 0) goto L1d5
            boolean r12 = defpackage.xjb.z()
            if (r12 == 0) goto L1d4
            goto L1d5
        L1d4:
            r2 = r3
        L1d5:
            boolean r12 = defpackage.ie8.i()
            if (r12 != 0) goto L1df
            boolean r2 = com.android.launcher3.anim.light.RecentsLightAnimUtil.isLightRecentAnim(r15)
        L1df:
            if (r2 == 0) goto L218
            boolean r12 = r11.y
            r11.getIsGesture()
            defpackage.s49.o(r14, r12, r15)
            float[] r12 = new float[r1]
            r12 = {x02bc: FILL_ARRAY_DATA , data: [0, 1065353216} // fill-array
            vb7 r13 = com.oplus.quickstep.views.OplusClearAllPanelView.R
            android.animation.PropertyValuesHolder r12 = android.animation.PropertyValuesHolder.ofFloat(r13, r12)
            android.animation.PropertyValuesHolder[] r12 = new android.animation.PropertyValuesHolder[]{r12}
            android.animation.ObjectAnimator r12 = android.animation.ObjectAnimator.ofPropertyValuesHolder(r11, r12)
            r12.getClass()
            uy r13 = defpackage.uy.s
            r13.getClass()
            boolean r13 = defpackage.uy.G1()
            if (r13 == 0) goto L20d
            r0 = 200(0xc8, double:9.9E-322)
            goto L20f
        L20d:
            r0 = 300(0x12c, double:1.48E-321)
        L20f:
            r12.setDuration(r0)
            vf1 r13 = com.android.launcher3.anim.light.RecentsLightAnimUtil.INTERPOLATOR_MOVE_EASE
            r12.setInterpolator(r13)
            goto L25f
        L218:
            android.animation.AnimatorSet r12 = new android.animation.AnimatorSet
            r12.<init>()
            boolean r13 = r11.y
            android.animation.PropertyValuesHolder r13 = defpackage.s49.o(r14, r13, r15)
            r2 = 0
            r4 = 4604029898846109696(0x3fe4ccccc0000000, double:0.6499999761581421)
            if (r13 == 0) goto L244
            android.animation.PropertyValuesHolder[] r13 = new android.animation.PropertyValuesHolder[]{r13}
            android.animation.ObjectAnimator r13 = android.animation.ObjectAnimator.ofPropertyValuesHolder(r11, r13)
            ao1 r15 = new ao1
            r15.<init>(r4, r2)
            r13.setInterpolator(r15)
            r6 = 0
            r13.setDuration(r6)
            r12.play(r13)
        L244:
            float[] r13 = new float[r1]
            r13 = {x02c4: FILL_ARRAY_DATA , data: [0, 1065353216} // fill-array
            android.util.Property r15 = android.view.View.ALPHA
            android.animation.ObjectAnimator r13 = android.animation.ObjectAnimator.ofFloat(r11, r15, r13)
            ao1 r15 = new ao1
            r15.<init>(r4, r2)
            r13.setInterpolator(r15)
            r0 = 500(0x1f4, double:2.47E-321)
            r13.setDuration(r0)
            r12.play(r13)
        L25f:
            r11.u = r12
            wb7 r13 = new wb7
            r13.<init>(r11, r14)
            r12.addListener(r13)
            android.animation.Animator r12 = r11.u
            r12.start()
            android.view.ViewParent r12 = r11.getParent()
            boolean r12 = r12 instanceof com.android.launcher3.RecentContainerView
            if (r12 == 0) goto L2aa
            java.lang.String r12 = defpackage.ia2.a
            boolean r12 = com.oplus.quickstep.common.settingsvalue.a.d()
            if (r12 == 0) goto L2aa
            android.view.ViewParent r11 = r11.getParent()
            com.android.launcher3.RecentContainerView r11 = (com.android.launcher3.RecentContainerView) r11
            com.android.quickstep.views.LauncherRecentsView r11 = r11.getMRecentView()
            kx8 r11 = r11.getRelayInteraction()
            n99 r12 = defpackage.n99.i
            r11.c(r12)
            return
        L292:
            r11 = move-exception
            android.os.Trace.endSection()
            throw r11
        L297:
            boolean r13 = r0 instanceof com.android.launcher3.LauncherState
            if (r13 == 0) goto L2a1
            com.android.launcher3.LauncherState r0 = (com.android.launcher3.LauncherState) r0
            boolean r13 = r0.overviewUi
            if (r13 == 0) goto L2ab
        L2a1:
            com.android.launcher3.LauncherState r13 = com.android.launcher3.LauncherState.NORMAL
            if (r12 == r13) goto L2ab
            com.android.launcher3.uioverrides.states.OplusAllAppsState r13 = com.android.launcher3.LauncherState.ALL_APPS
            if (r12 != r13) goto L2aa
            goto L2ab
        L2aa:
            return
        L2ab:
            r11.b()
            r11.d(r12)
            java.lang.String r12 = "playEnterAnimation"
            r11.t(r12, r2, r3)
            r11.setViewClickable(r6)
            return
    }

    @Override // android.view.View
    public void setAlpha(float r5) {
            r4 = this;
            super.setAlpha(r5)
            com.android.launcher.views.PressFeedbackButton r0 = r4.c
            if (r0 != 0) goto L8
            goto L31
        L8:
            r1 = 0
            int r1 = (r5 > r1 ? 1 : (r5 == r1 ? 0 : -1))
            if (r1 > 0) goto L1a
            int r0 = r0.getVisibility()
            r1 = 4
            if (r0 == r1) goto L31
            com.android.launcher.views.PressFeedbackButton r4 = r4.c
            r4.setVisibility(r1)
            goto L31
        L1a:
            int r0 = r0.getVisibility()
            if (r0 == 0) goto L31
            com.android.launcher.views.PressFeedbackButton r0 = r4.c
            r1 = 0
            r0.setVisibility(r1)
            boolean r0 = com.android.launcher3.util.LauncherMaterialEffectUtils.isBlurEnabled()
            if (r0 == 0) goto L31
            com.android.launcher.views.PressFeedbackButton r4 = r4.c
            r4.invalidate()
        L31:
            boolean r4 = defpackage.q36.u()
            if (r4 == 0) goto L55
            double r0 = (double) r5
            r2 = 0
            int r4 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1))
            if (r4 == 0) goto L44
            r2 = 4607182418800017408(0x3ff0000000000000, double:1.0)
            int r4 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1))
            if (r4 != 0) goto L55
        L44:
            java.lang.String r4 = "alpha: "
            java.lang.String r0 = ", caller="
            java.lang.StringBuilder r4 = defpackage.mu1.c(r4, r0, r5)
            r5 = 10
            java.lang.String r0 = ""
            java.lang.String r1 = "OplusClearAllPanelView"
            defpackage.ug5.a(r5, r0, r1, r4)
        L55:
            return
    }

    public void setContentAlpha(float r1) {
            r0 = this;
            return
    }

    @Override // defpackage.ej4
    public void setDrawFocusFlag(boolean r1) {
            r0 = this;
            r0.N = r1
            return
    }

    @Override // defpackage.ej4
    public void setDrawHoverFlag(boolean r1) {
            r0 = this;
            r0.O = r1
            return
    }

    public void setGridProgress(float r1) {
            r0 = this;
            return
    }

    public void setGridScrollOffset(float r1) {
            r0 = this;
            return
    }

    public void setGridTranslationPrimary(float r1) {
            r0 = this;
            return
    }

    public void setGridTranslationSecondary(float r1) {
            r0 = this;
            return
    }

    public void setIsRecentsViewPortrait(boolean r1) {
            r0 = this;
            r0.y = r1
            return
    }

    public void setOffsetTranslationPrimary(float r1) {
            r0 = this;
            return
    }

    public void setOnClearClickListener(android.view.View.OnClickListener r2) {
            r1 = this;
            if (r2 != 0) goto L3
            return
        L3:
            qb7 r0 = new qb7
            r0.<init>()
            r0.c = r1
            r0.q = r2
            java.lang.invoke.VarHandle.storeStoreFence()
            com.android.launcher.views.PressFeedbackButton r1 = r1.c
            r1.setOnClickListener(r0)
            return
    }

    public void setOrientationState(defpackage.a19 r1) {
            r0 = this;
            return
    }

    @Override // android.view.View
    public void setTranslationY(float r4) {
            r3 = this;
            com.android.launcher3.util.MainThreadInitializedObject<com.android.launcher3.util.DisplayController> r0 = com.android.launcher3.util.DisplayController.INSTANCE
            android.content.Context r1 = r3.getContext()
            java.lang.Object r0 = r0.get(r1)
            com.android.launcher3.util.DisplayController r0 = (com.android.launcher3.util.DisplayController) r0
            com.android.launcher3.util.DisplayController$Info r0 = r0.getInfo()
            int r0 = r0.rotation
            android.content.Context r1 = r3.mContext
            boolean r2 = r1 instanceof com.android.launcher.Launcher
            if (r2 != 0) goto L26
            if (r0 == 0) goto L26
            android.content.res.Resources r0 = r1.getResources()
            r1 = 2131166052(0x7f070364, float:1.7946338E38)
            int r0 = r0.getDimensionPixelSize(r1)
            goto L27
        L26:
            r0 = 0
        L27:
            float r0 = (float) r0
            float r4 = r4 + r0
            super.setTranslationY(r4)
            return
    }

    public void setViewClickable(float r7) {
            r6 = this;
            com.android.launcher.views.PressFeedbackButton r0 = r6.c
            r1 = 0
            r2 = 0
            r3 = 1
            if (r0 == 0) goto L1b
            int r4 = (r7 > r1 ? 1 : (r7 == r1 ? 0 : -1))
            if (r4 <= 0) goto Ld
            r5 = r3
            goto Le
        Ld:
            r5 = r2
        Le:
            r0.setClickable(r5)
            com.android.launcher.views.PressFeedbackButton r0 = r6.c
            if (r4 <= 0) goto L17
            r4 = r3
            goto L18
        L17:
            r4 = r2
        L18:
            r0.setLongClickable(r4)
        L1b:
            com.oplus.quickstep.views.OplusCleanStorageView r0 = r6.r
            if (r0 == 0) goto L31
            int r7 = (r7 > r1 ? 1 : (r7 == r1 ? 0 : -1))
            if (r7 <= 0) goto L25
            r1 = r3
            goto L26
        L25:
            r1 = r2
        L26:
            r0.setClickable(r1)
            com.oplus.quickstep.views.OplusCleanStorageView r6 = r6.r
            if (r7 <= 0) goto L2e
            r2 = r3
        L2e:
            r6.setLongClickable(r2)
        L31:
            return
    }

    public void setVisibilityAlpha(float r2) {
            r1 = this;
            float r0 = r1.getAlpha()
            int r0 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1))
            if (r0 == 0) goto Lb
            r1.setAlpha(r2)
        Lb:
            return
    }

    @Override // defpackage.ej4
    public final boolean shouldUseStandardRoundRect() {
            r0 = this;
            r0 = 1
            return r0
    }

    public final void t(java.lang.String r15, boolean r16, boolean r17) {
            r14 = this;
            r0 = r16
            com.android.launcher3.states.OPlusBaseState r1 = com.android.launcher3.states.OPlusBaseState.getTargetLauncherState()
            r2 = 0
            r3 = 1
            if (r1 != 0) goto Lb
            goto L14
        Lb:
            com.android.launcher3.uioverrides.states.OverviewState r4 = com.android.launcher3.LauncherState.OVERVIEW
            if (r1 == r4) goto L16
            h19 r4 = defpackage.h19.j
            if (r1 != r4) goto L14
            goto L16
        L14:
            r4 = r2
            goto L17
        L16:
            r4 = r3
        L17:
            boolean r5 = r14.j()
            android.content.Context r6 = r14.getContext()
            com.android.launcher3.BaseActivity r6 = com.android.launcher3.BaseActivity.fromContext(r6)
            boolean r7 = r6 instanceof com.android.launcher3.BaseDraggingActivity
            r8 = 0
            if (r7 == 0) goto L2b
            com.android.launcher3.BaseDraggingActivity r6 = (com.android.launcher3.BaseDraggingActivity) r6
            goto L2c
        L2b:
            r6 = r8
        L2c:
            if (r6 == 0) goto L33
            android.view.View r6 = r6.getOverviewPanel()
            goto L34
        L33:
            r6 = r8
        L34:
            boolean r7 = r6 instanceof defpackage.zo7
            if (r7 == 0) goto L3b
            zo7 r6 = (defpackage.zo7) r6
            goto L3c
        L3b:
            r6 = r8
        L3c:
            if (r6 == 0) goto L46
            boolean r6 = r6.getSpringAnimToRecent()
            if (r6 == 0) goto L46
            r6 = r3
            goto L47
        L46:
            r6 = r2
        L47:
            boolean r7 = defpackage.s49.m
            boolean r9 = defpackage.q36.u()
            java.lang.String r10 = "OplusClearAllPanelView"
            java.lang.String r11 = ""
            if (r9 == 0) goto L8b
            java.lang.String r9 = ", animate="
            java.lang.String r12 = ", invokeFrom="
            java.lang.String r13 = "playExitAnimator(), visible="
            java.lang.StringBuilder r9 = defpackage.c.c(r13, r4, r9, r0, r12)
            r9.append(r15)
            java.lang.String r12 = ", alpha="
            r9.append(r12)
            float r12 = r14.getAlpha()
            r9.append(r12)
            java.lang.String r12 = " , targetState = "
            r9.append(r12)
            r9.append(r1)
            java.lang.String r12 = ", isAppToHomeSwipeToRecentTogetherRunning="
            r9.append(r12)
            r9.append(r7)
            java.lang.String r12 = "; isSpringAnimToRecent="
            r9.append(r12)
            java.lang.String r12 = ", isEnterAnimatorRunning="
            java.lang.String r9 = defpackage.vj1.e(r9, r6, r12, r5)
            defpackage.q36.p(r11, r10, r9)
        L8b:
            if (r7 == 0) goto L97
            com.android.launcher3.uioverrides.states.OverviewState r9 = com.android.launcher3.LauncherState.OVERVIEW
            if (r1 != r9) goto L97
            if (r6 == 0) goto L97
            if (r5 == 0) goto L97
            goto L210
        L97:
            com.android.launcher3.uioverrides.states.OverviewState r9 = com.android.launcher3.LauncherState.OVERVIEW
            if (r1 != r9) goto La1
            if (r6 == 0) goto La1
            if (r5 != 0) goto La1
            r6 = r3
            goto La2
        La1:
            r6 = r2
        La2:
            if (r7 == 0) goto Lab
            if (r6 == 0) goto Lab
            if (r0 != 0) goto Lab
            if (r4 == 0) goto Lab
            r4 = r2
        Lab:
            r6 = 1065353216(0x3f800000, float:1.0)
            if (r4 != 0) goto Lbb
            if (r5 == 0) goto Lbb
            android.animation.Animator r5 = r14.u
            if (r5 == 0) goto Lbb
            r5.end()
            r14.setAlpha(r6)
        Lbb:
            if (r4 != 0) goto Lc6
            mc r5 = r14.x
            if (r5 == 0) goto Lc6
            r5.dismiss()
            r14.x = r8
        Lc6:
            wc8 r5 = defpackage.wc8.a
            boolean r5 = defpackage.im9.q()
            if (r5 == 0) goto Ldf
            nq1 r5 = defpackage.tc8.c
            if (r5 == 0) goto Ldf
            boolean r5 = r5.isShowing()
            if (r5 != r3) goto Ldf
            nq1 r5 = defpackage.tc8.c
            if (r5 == 0) goto Ldf
            r5.dismiss()
        Ldf:
            r5 = 0
            if (r4 == 0) goto Le3
            goto Le4
        Le3:
            r6 = r5
        Le4:
            if (r0 == 0) goto L1d7
            float r0 = r14.getAlpha()
            int r0 = (r6 > r0 ? 1 : (r6 == r0 ? 0 : -1))
            if (r0 != 0) goto Lf0
            goto L1d7
        Lf0:
            int r0 = r14.getVisibility()
            if (r0 != 0) goto Lff
            float r0 = r14.getAlpha()
            int r0 = (r0 > r5 ? 1 : (r0 == r5 ? 0 : -1))
            if (r0 <= 0) goto Lff
            goto L108
        Lff:
            if (r1 != r9) goto L108
            java.lang.String r14 = "playExitAnimator(), return because OplusClearAllPanelView not visible, no need exit"
            defpackage.q36.d(r11, r10, r14, r8)
            return
        L108:
            vb7 r0 = com.oplus.quickstep.views.OplusClearAllPanelView.R
            boolean r1 = r14.L
            if (r1 == 0) goto L12c
            java.lang.String r14 = "startExitAnimator now is exit animating and return"
            defpackage.q36.I(r11, r10, r14, r8)
            boolean r14 = defpackage.im9.q()
            if (r14 == 0) goto L210
            nq1 r14 = defpackage.tc8.c
            if (r14 == 0) goto L210
            boolean r14 = r14.isShowing()
            if (r14 != r3) goto L210
            nq1 r14 = defpackage.tc8.c
            if (r14 == 0) goto L210
            r14.dismiss()
            return
        L12c:
            android.animation.Animator r1 = r14.s
            if (r1 == 0) goto L13b
            boolean r1 = r1.isStarted()
            if (r1 == 0) goto L13b
            android.animation.Animator r1 = r14.s
            r1.cancel()
        L13b:
            r1 = 2
            if (r17 == 0) goto L1b2
            float r0 = r14.getAlpha()
            float r4 = r14.getTranslationY()
            android.animation.AnimatorSet r6 = new android.animation.AnimatorSet
            r6.<init>()
            boolean r7 = r14.y
            int[] r9 = new int[r1]
            r14.getLocationInWindow(r9)
            int r10 = r14.i(r2)
            r9 = r9[r3]
            int r10 = r10 - r9
            int r9 = r14.getHeight()
            int r9 = r9 + r10
            float r9 = (float) r9
            java.lang.String r10 = ", startLocation = "
            java.lang.String r11 = "  endLocation = "
            java.lang.String r12 = "getTranslationPropForClearAllPanel, rotation = 0, isRecentsViewPortrait = "
            java.lang.StringBuilder r10 = defpackage.gb2.f(r12, r7, r10, r4, r11)
            java.lang.String r11 = " "
            java.lang.String r10 = defpackage.q9.d(r10, r9, r11)
            java.lang.String r11 = "RecentsViewAnimUtil"
            defpackage.q36.d(r8, r11, r10, r8)
            uy r8 = defpackage.uy.s
            r8.getClass()
            boolean r8 = defpackage.uy.G1()
            if (r8 == 0) goto L180
            goto L18e
        L180:
            if (r7 != 0) goto L183
            goto L18e
        L183:
            float[] r7 = new float[r1]
            r7[r2] = r4
            r7[r3] = r9
            android.util.Property r4 = android.view.ViewGroup.TRANSLATION_Y
            android.animation.PropertyValuesHolder.ofFloat(r4, r7)
        L18e:
            float[] r1 = new float[r1]
            r1[r2] = r0
            r1[r3] = r5
            android.util.Property r0 = android.view.View.ALPHA
            android.animation.ObjectAnimator r0 = android.animation.ObjectAnimator.ofFloat(r14, r0, r1)
            android.view.animation.LinearInterpolator r1 = com.android.launcher3.anim.Interpolators.LINEAR
            r0.setInterpolator(r1)
            r6.play(r0)
            r0 = 120(0x78, double:5.93E-322)
            r6.setDuration(r0)
            yb7 r0 = new yb7
            r0.<init>(r14)
            r6.addListener(r0)
            r14.s = r6
            goto L1ce
        L1b2:
            float r4 = r14.getAlpha()
            float[] r1 = new float[r1]
            r1[r2] = r4
            r1[r3] = r5
            android.animation.ObjectAnimator r0 = android.animation.ObjectAnimator.ofFloat(r14, r0, r1)
            r14.s = r0
            android.view.animation.OplusBezierInterpolator r1 = defpackage.xg7.a
            r0.setInterpolator(r1)
            android.animation.Animator r0 = r14.s
            r3 = 150(0x96, double:7.4E-322)
            r0.setDuration(r3)
        L1ce:
            r14.q(r2)
            android.animation.Animator r14 = r14.s
            r14.start()
            return
        L1d7:
            android.animation.Animator r0 = r14.s
            if (r0 == 0) goto L1e6
            boolean r0 = r0.isStarted()
            if (r0 == 0) goto L1e6
            android.animation.Animator r0 = r14.s
            r0.cancel()
        L1e6:
            r14.setVisibilityAlpha(r6)
            tb0 r0 = new tb0
            r1 = 5
            r0.<init>(r1)
            r0.q = r14
            java.lang.invoke.VarHandle.storeStoreFence()
            r14.post(r0)
            r14.setViewClickable(r6)
            zq7 r0 = defpackage.zq7.a
            boolean r0 = defpackage.j28.b()
            if (r0 != 0) goto L209
            int r0 = (r6 > r5 ? 1 : (r6 == r5 ? 0 : -1))
            if (r0 <= 0) goto L209
            r14.setVisibilityAlpha(r5)
        L209:
            int r0 = (r6 > r5 ? 1 : (r6 == r5 ? 0 : -1))
            if (r0 != 0) goto L210
            r14.q(r3)
        L210:
            return
    }

    @Override // android.view.View
    public final java.lang.String toString() {
            r2 = this;
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            java.lang.String r1 = super.toString()
            r0.<init>(r1)
            java.lang.String r1 = "{transX="
            r0.append(r1)
            float r1 = r2.getTranslationX()
            r0.append(r1)
            java.lang.String r1 = ", transY="
            r0.append(r1)
            float r1 = r2.getTranslationY()
            r0.append(r1)
            java.lang.String r1 = ", transZ="
            r0.append(r1)
            float r2 = r2.getTranslationZ()
            r0.append(r2)
            java.lang.String r2 = "}"
            r0.append(r2)
            java.lang.String r2 = r0.toString()
            return r2
    }

    public final void u(java.lang.String r4, boolean r5) {
            r3 = this;
            java.lang.String r0 = "setClearAllPanelViewTranslationZ isRecover="
            java.lang.String r1 = ", translationZ="
            java.lang.StringBuilder r0 = defpackage.br0.h(r0, r1, r5)
            float r1 = r3.getTranslationZ()
            r0.append(r1)
            java.lang.String r1 = ", reason="
            r0.append(r1)
            r0.append(r4)
            java.lang.String r0 = r0.toString()
            r1 = 0
            java.lang.String r2 = "OplusClearAllPanelView"
            defpackage.q36.p(r1, r2, r0)
            if (r5 == 0) goto L32
            float r5 = r3.getTranslationZ()
            float r0 = r3.I
            int r5 = (r5 > r0 ? 1 : (r5 == r0 ? 0 : -1))
            if (r5 == 0) goto L3f
            r3.setTranslationZ(r0)
            goto L3f
        L32:
            float r5 = r3.getTranslationZ()
            r0 = -822083584(0xffffffffcf000000, float:-2.1474836E9)
            int r5 = (r5 > r0 ? 1 : (r5 == r0 ? 0 : -1))
            if (r5 == 0) goto L3f
            r3.setTranslationZ(r0)
        L3f:
            r3.J = r4
            return
    }

    public final void v() {
            r4 = this;
            android.content.Context r0 = r4.mContext
            defpackage.py2.b(r0)
            android.content.res.Resources r0 = r0.getResources()
            com.android.launcher.views.PressFeedbackButton r1 = r4.c
            android.view.ViewGroup$LayoutParams r1 = r1.getLayoutParams()
            r2 = 2131166042(0x7f07035a, float:1.7946318E38)
            int r2 = r0.getDimensionPixelSize(r2)
            boolean r3 = defpackage.d01.C()
            if (r3 == 0) goto L23
            r2 = 2131169843(0x7f071233, float:1.7954028E38)
            int r2 = r0.getDimensionPixelSize(r2)
        L23:
            int r0 = r1.width
            if (r2 == r0) goto L2e
            r1.width = r2
            com.android.launcher.views.PressFeedbackButton r4 = r4.c
            r4.setLayoutParams(r1)
        L2e:
            return
    }

    public final void w() {
            r3 = this;
            android.widget.TextView r0 = r3.t
            android.view.ViewGroup$LayoutParams r0 = r0.getLayoutParams()
            android.widget.LinearLayout$LayoutParams r0 = (android.widget.LinearLayout.LayoutParams) r0
            android.content.Context r1 = r3.mContext
            android.content.res.Resources r1 = r1.getResources()
            r2 = 2131166111(0x7f07039f, float:1.7946458E38)
            int r1 = r1.getDimensionPixelSize(r2)
            int r2 = r0.topMargin
            if (r2 == r1) goto L20
            r0.topMargin = r1
            android.widget.TextView r3 = r3.t
            r3.setLayoutParams(r0)
        L20:
            return
    }

    public final void x() {
            r3 = this;
            android.widget.TextView r0 = r3.t
            android.view.ViewGroup$LayoutParams r0 = r0.getLayoutParams()
            android.widget.LinearLayout$LayoutParams r0 = (android.widget.LinearLayout.LayoutParams) r0
            android.content.Context r1 = r3.mContext
            android.content.res.Resources r1 = r1.getResources()
            r2 = 2131166112(0x7f0703a0, float:1.794646E38)
            int r1 = r1.getDimensionPixelSize(r2)
            int r2 = r0.topMargin
            if (r2 == r1) goto L20
            r0.topMargin = r1
            android.widget.TextView r3 = r3.t
            r3.setLayoutParams(r0)
        L20:
            return
    }

    public final void y(boolean r4) {
            r3 = this;
            android.content.Context r0 = r3.mContext
            zg6 r0 = defpackage.zg6.a(r0)
            boolean r1 = r0.d
            if (r1 == 0) goto L20
            com.oplus.dispatch.OCDExecutorService r1 = defpackage.md7.d()
            rb7 r2 = new rb7
            r2.<init>()
            r2.c = r3
            r2.q = r0
            r2.r = r4
            java.lang.invoke.VarHandle.storeStoreFence()
            r1.submit(r2)
            return
        L20:
            android.widget.TextView r4 = r3.t
            java.lang.CharSequence r4 = r4.getText()
            boolean r4 = android.text.TextUtils.isEmpty(r4)
            if (r4 != 0) goto L3a
            android.widget.TextView r4 = r3.t
            java.lang.String r0 = ""
            r4.setText(r0)
            android.widget.TextView r3 = r3.t
            r4 = 8
            r3.setVisibility(r4)
        L3a:
            return
    }

    public final void z(int r8) {
            r7 = this;
            wc8 r0 = defpackage.wc8.a
            boolean r0 = defpackage.im9.q()
            if (r0 == 0) goto L55
            com.oplus.quickstep.views.OplusCleanStorageView r0 = r7.r
            android.view.ViewGroup$LayoutParams r0 = r0.getLayoutParams()
            android.widget.RelativeLayout$LayoutParams r0 = (android.widget.RelativeLayout.LayoutParams) r0
            android.content.Context r1 = r7.mContext
            android.content.res.Resources r1 = r1.getResources()
            r2 = 2131166049(0x7f070361, float:1.7946332E38)
            int r1 = r1.getDimensionPixelSize(r2)
            android.content.Context r2 = r7.mContext
            com.android.launcher3.util.DisplayController$NavigationMode r2 = com.android.launcher3.util.DisplayController.getNavigationMode(r2)
            com.android.launcher3.util.DisplayController$NavigationMode r3 = com.android.launcher3.util.DisplayController.NavigationMode.THREE_BUTTONS
            if (r2 != r3) goto L4a
            r2 = 3
            if (r8 != r2) goto L4a
            android.content.Context r8 = r7.mContext
            r2 = 0
            if (r8 != 0) goto L30
            goto L49
        L30:
            android.content.res.Resources r3 = r8.getResources()
            java.lang.String r4 = "dimen"
            java.lang.String r5 = "android"
            java.lang.String r6 = "navigation_bar_height"
            int r3 = r3.getIdentifier(r6, r4, r5)
            if (r3 <= 0) goto L49
            android.content.res.Resources r8 = r8.getResources()
            int r2 = r8.getDimensionPixelSize(r3)
        L49:
            int r1 = r1 + r2
        L4a:
            int r8 = r0.leftMargin
            if (r8 == r1) goto L55
            r0.leftMargin = r1
            com.oplus.quickstep.views.OplusCleanStorageView r7 = r7.r
            r7.setLayoutParams(r0)
        L55:
            return
    }
}
