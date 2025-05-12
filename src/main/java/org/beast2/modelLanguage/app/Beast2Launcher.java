package org.beast2.modelLanguage.app;

import beast.pkgmgmt.PackageManager;
import beast.pkgmgmt.launcher.BeastLauncher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Beast2Launcher extends BeastLauncher {


    public static void main(String[] args) {
        String classpath = null;
        try {
            // boolean useStrictVersions, String beastFile
            classpath = getPath(false, null);

        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            PackageManager.loadExternalJars();
        } catch (IOException e) {
            e.printStackTrace();
        }

        run(classpath, "org.beast2.modelLanguage.Beast2Lang", args);
    }

}
