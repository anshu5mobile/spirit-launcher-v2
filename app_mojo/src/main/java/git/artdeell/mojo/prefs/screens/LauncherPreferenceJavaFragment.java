package git.artdeell.mojo.prefs.screens;

import static git.artdeell.mojo.Architecture.is32BitsDevice;
import static git.artdeell.mojo.Tools.getTotalDeviceMemory;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.EditTextPreference;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.perf.AutoAllocate;
import git.artdeell.mojo.utils.SpiritToast;
import git.artdeell.mojo.multirt.MultiRTConfigDialog;
import git.artdeell.mojo.prefs.CustomSeekBarPreference;
import git.artdeell.mojo.prefs.LauncherPreferences;

public class LauncherPreferenceJavaFragment extends LauncherPreferenceFragment {
    private MultiRTConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mVmInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), (data)->{
                if(data != null) Tools.installRuntimeFromUri(getContext(), data);
            });

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        int ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION;
        // Triggers a write for some reason
        addPreferencesFromResource(R.xml.pref_java);

        CustomSeekBarPreference memorySeekbar = requirePreference("allocation",
                CustomSeekBarPreference.class);

        int maxRAM;
        int deviceRam = getTotalDeviceMemory(memorySeekbar.getContext());

        if(is32BitsDevice() || deviceRam < 2048) maxRAM = Math.min(1024, deviceRam);
        else maxRAM = deviceRam - (deviceRam < 3064 ? 800 : 1024); //To have a minimum for the device to breathe

        memorySeekbar.setMaxKeepIncrement(maxRAM);
        memorySeekbar.setValue(ramAllocation);
        memorySeekbar.setSuffix(" MB");

        final int minRam = getResources().getInteger(R.integer.memory_seekbar_min);
        final int stepRam = getResources().getInteger(R.integer.memory_seekbar_increment);
        final int maxAllowed = maxRAM;
        requirePreference("spiritAutoAllocate").setOnPreferenceClickListener(preference -> {
            AutoAllocate.Result r = AutoAllocate.compute(requireContext(), maxAllowed, minRam, stepRam);
            memorySeekbar.setValue(r.allocationMb);
            LauncherPreferences.PREF_RAM_ALLOCATION = r.allocationMb;
            SpiritToast.makeText(requireContext(), getString(R.string.spirit_autoalloc_done, r.allocationMb, r.totalMb, r.freeMb),
                    android.widget.Toast.LENGTH_LONG).show();
            updateDeviceInfo(r.allocationMb);
            return true;
        });
        updateDeviceInfo(ramAllocation);

        EditTextPreference editJVMArgs = findPreference("javaArgs");
        if (editJVMArgs != null) {
            editJVMArgs.setOnBindEditTextListener(TextView::setSingleLine);
        }

        requirePreference("install_jre").setOnPreferenceClickListener(preference->{
            openMultiRTDialog();
            return true;
        });
    }

    private void updateDeviceInfo(int currentMb) {
        android.content.Context c = getContext();
        if(c == null) return;
        AutoAllocate.Result r = AutoAllocate.compute(c, Integer.MAX_VALUE, 256, 8);
        requirePreference("spiritDeviceInfo").setSummary(getString(R.string.spirit_device_info,
                r.totalMb, r.freeMb, android.os.Build.VERSION.RELEASE, Runtime.getRuntime().availableProcessors(), r.allocationMb, currentMb));
    }

    private void openMultiRTDialog() {
        if (mDialogScreen == null) {
            mDialogScreen = new MultiRTConfigDialog();
            mDialogScreen.prepare(getContext(), mVmInstallLauncher);
        }
        mDialogScreen.show();
    }
}
