package in.dragons.galaxy;

import android.content.Context;
import android.util.Log;

import in.dragons.galaxy.model.App;

import java.io.File;

abstract public class DeltaPatcherAbstract {

    protected App app;
    protected Context context;
    protected File patch;

    public DeltaPatcherAbstract(Context context, App app) {
        Log.i(getClass().getSimpleName(), "Chosen delta patcher");
        this.app = app;
        this.context = context;
        patch = Paths.getDeltaPath(context, app.getPackageName(), app.getVersionCode());
    }

    abstract boolean patch();
}