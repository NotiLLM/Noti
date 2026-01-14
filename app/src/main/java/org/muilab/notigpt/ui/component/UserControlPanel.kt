package org.muilab.notigpt.ui.component

import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun UserControlPanel(drawerViewModel: DrawerViewModel) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(5.dp))
        TextButton(
            onClick = { (context as? ComponentActivity)?.finish() },
        ) {
            Text(stringResource(R.string.ui_user_close_app))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = {
                drawerViewModel.deleteAllNotis()
                Toast.makeText(context, context.getString(R.string.ui_user_all_notifications_deleted), Toast.LENGTH_SHORT).show()
            },
        ) {
            Text(stringResource(R.string.ui_user_clear_all))
        }
        Spacer(Modifier.size(5.dp))
    }
}