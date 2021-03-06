/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.shell;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.event.CommandActionEvent;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.pointer.VirtualKeyboard;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.midlet.MIDlet;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ConfigActivity;
import ru.playsoftware.j2meloader.util.FileUtils;

public class MicroActivity extends AppCompatActivity {
	private static final String TAG = MicroActivity.class.getName();
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;

	private Displayable current;
	private boolean visible;
	private boolean loaded;
	private boolean started;
	private boolean actionBarEnabled;
	private LinearLayout layout;
	private Toolbar toolbar;
	private String pathToMidletDir;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		setTheme(sp.getString("pref_theme", "light"));
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_micro);
		ContextHolder.setCurrentActivity(this);
		layout = findViewById(R.id.displayable_container);
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		actionBarEnabled = sp.getBoolean("pref_actionbar_switch", false);
		boolean wakelockEnabled = sp.getBoolean("pref_wakelock_switch", false);
		if (wakelockEnabled) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		Intent intent = getIntent();
		int orientation = intent.getIntExtra(ConfigActivity.MIDLET_ORIENTATION_KEY, ORIENTATION_DEFAULT);
		setOrientation(orientation);
		pathToMidletDir = intent.getStringExtra(ConfigActivity.MIDLET_PATH_KEY);
		initEmulator();
		try {
			loadMIDlet();
		} catch (Exception e) {
			e.printStackTrace();
			showErrorDialog(e.getMessage());
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		visible = true;
		if (loaded) {
			if (started) {
				Display.getDisplay(null).activityResumed();
			} else {
				started = true;
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		visible = false;
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (loaded) {
			Display.getDisplay(null).activityStopped();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && current != null && current instanceof Canvas) {
			hideSystemUI();
		}
	}

	private void setOrientation(int orientation) {
		switch (orientation) {
			case ORIENTATION_AUTO:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				break;
			case ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
				break;
			case ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				break;
			case ORIENTATION_DEFAULT:
			default:
				break;
		}
	}

	private void setTheme(String theme) {
		if (theme.equals("dark")) {
			setTheme(R.style.AppTheme_NoActionBar);
		} else {
			setTheme(R.style.AppTheme_Light_NoActionBar);
		}
	}

	private void loadMIDlet() throws Exception {
		ArrayList<String> midlets = new ArrayList<>();
		LinkedHashMap<String, String> params = FileUtils.loadManifest(new File(pathToMidletDir + Config.MIDLET_MANIFEST_FILE));
		MIDlet.initProps(params);
		for (Map.Entry<String, String> entry : params.entrySet()) {
			if (entry.getKey().matches("MIDlet-[0-9]+")) {
				midlets.add(entry.getValue());
			}
		}
		int size = midlets.size();
		String[] midletsNameArray = new String[size];
		String[] midletsClassArray = new String[size];
		for (int i = 0; i < size; i++) {
			String tmp = midlets.get(i);
			midletsClassArray[i] = tmp.substring(tmp.lastIndexOf(',') + 1).trim();
			midletsNameArray[i] = tmp.substring(0, tmp.indexOf(',')).trim();
		}
		if (size == 0) {
			throw new Exception();
		} else if (size == 1) {
			startMidlet(midletsClassArray[0]);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] midletsNameArray, final String[] midletsClassArray) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(midletsNameArray, (d, n) -> startMidlet(midletsClassArray[n]))
				.setOnCancelListener(dialogInterface -> finish());
		builder.show();
	}

	private void startMidlet(String mainClass) {
		File dexSource = new File(pathToMidletDir, Config.MIDLET_DEX_FILE);
		File dexTargetDir = new File(getApplicationInfo().dataDir, Config.TEMP_DEX_DIR);
		if (dexTargetDir.exists()) {
			FileUtils.deleteDirectory(dexTargetDir);
		}
		dexTargetDir.mkdir();
		File dexTargetOptDir = new File(getApplicationInfo().dataDir, Config.TEMP_DEX_OPT_DIR);
		if (dexTargetOptDir.exists()) {
			FileUtils.deleteDirectory(dexTargetOptDir);
		}
		dexTargetOptDir.mkdir();
		File dexTarget = new File(dexTargetDir, Config.MIDLET_DEX_FILE);
		try {
			FileUtils.copyFileUsingChannel(dexSource, dexTarget);
			ClassLoader loader = new MyClassLoader(dexTarget.getAbsolutePath(),
					dexTargetOptDir.getAbsolutePath(), null, getClassLoader(), pathToMidletDir + Config.MIDLET_RES_DIR);
			Log.i(TAG, "load main: " + mainClass + " from dex:" + dexTarget.getPath());
			final MIDlet midlet = (MIDlet) loader.loadClass(mainClass).newInstance();
			// Start midlet in Thread
			Runnable r = () -> {
				try {
					midlet.startApp();
					loaded = true;
				} catch (Throwable t) {
					t.printStackTrace();
					ContextHolder.notifyDestroyed();
				}
			};
			(new Thread(r, "MIDletLoader")).start();
		} catch (Throwable t) {
			t.printStackTrace();
			showErrorDialog(t.getMessage());
		}
	}

	private void initEmulator() {
		Display.initDisplay();
		Graphics3D.initGraphics3D();
		File cacheDir = ContextHolder.getCacheDir();
		if (cacheDir.exists()) {
			for (File temp : cacheDir.listFiles()) {
				temp.delete();
			}
		}
	}

	private void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message);
		builder.setOnCancelListener(dialogInterface -> ContextHolder.notifyDestroyed());
		builder.show();
	}

	private SimpleEvent msgSetCurent = new SimpleEvent() {
		@Override
		public void process() {
			current.setParentActivity(MicroActivity.this);
			layout.removeAllViews();
			layout.addView(current.getDisplayableView());
			invalidateOptionsMenu();
			ActionBar actionBar = getSupportActionBar();
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) toolbar.getLayoutParams();
			if (current instanceof Canvas) {
				hideSystemUI();
				if (!actionBarEnabled) {
					actionBar.hide();
				} else {
					actionBar.setTitle(MyClassLoader.getName());
					layoutParams.height = (int) (getToolBarHeight() / 1.5);
				}
			} else {
				showSystemUI();
				actionBar.show();
				actionBar.setTitle(current.getTitle());
				layoutParams.height = getToolBarHeight();
			}
			toolbar.setLayoutParams(layoutParams);
		}
	};

	private int getToolBarHeight() {
		int[] attrs = new int[]{R.attr.actionBarSize};
		TypedArray ta = obtainStyledAttributes(attrs);
		int toolBarHeight = ta.getDimensionPixelSize(0, -1);
		ta.recycle();
		return toolBarHeight;
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		} else {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	private void showSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public void setCurrent(Displayable disp) {
		current = disp;
		runOnUiThread(msgSetCurent);
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return visible;
	}

	private void showExitConfirmation() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setTitle(R.string.CONFIRMATION_REQUIRED)
				.setMessage(R.string.FORCE_CLOSE_CONFIRMATION)
				.setPositiveButton(android.R.string.yes, (p1, p2) -> {
					Runnable r = () -> {
						try {
							Display.getDisplay(null).activityDestroyed();
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						ContextHolder.notifyDestroyed();
					};
					(new Thread(r, "MIDletDestroyThread")).start();
				})
				.setNegativeButton(android.R.string.no, null);
		alertBuilder.create().show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				openOptionsMenu();
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (current != null) {
			menu.clear();
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.midlet, menu);
			for (Command cmd : current.getCommands()) {
				menu.add(Menu.NONE, cmd.hashCode(), Menu.NONE, cmd.getLabel());
			}
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (current != null) {
			int id = item.getItemId();
			if (item.getGroupId() == R.id.action_group_common_settings) {
				if (id == R.id.action_exit_midlet) {
					showExitConfirmation();
				} else if (current instanceof Canvas && ContextHolder.getVk() != null) {
					VirtualKeyboard vk = ContextHolder.getVk();
					switch (id) {
						case R.id.action_layout_edit_mode:
							vk.switchLayoutEditMode(VirtualKeyboard.LAYOUT_KEYS);
							break;
						case R.id.action_layout_scale_mode:
							vk.switchLayoutEditMode(VirtualKeyboard.LAYOUT_SCALES);
							break;
						case R.id.action_layout_edit_finish:
							vk.switchLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
							break;
						case R.id.action_layout_switch:
							vk.switchLayout();
							break;
						case R.id.action_hide_buttons:
							showHideButtonDialog();
							break;
					}
				}
				return true;
			}

			CommandListener listener = current.getCommandListener();
			if (listener == null) {
				return false;
			}

			for (Command cmd : current.getCommands()) {
				if (cmd.hashCode() == id) {
					current.postEvent(CommandActionEvent.getInstance(listener, cmd, current));
					return true;
				}
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(vk.getKeyNames(), vk.getKeyVisibility(), (dialogInterface, i, b) -> vk.setKeyVisibility(i, b))
				.setPositiveButton(android.R.string.ok, null);
		builder.show();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		}

		return super.onContextItemSelected(item);
	}
}
