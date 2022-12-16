package com.demo.notecam;

import com.google.android.material.button.MaterialButton;

public interface OnPermissionDialogButtonClickListener {
    void onPositiveButtonClicked(MaterialButton button);
    void onNegativeButtonClicked(MaterialButton button);
}
