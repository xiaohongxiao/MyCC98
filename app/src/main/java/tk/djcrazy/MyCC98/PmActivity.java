package tk.djcrazy.MyCC98;

import roboguice.inject.ContentView;
import roboguice.inject.InjectView;
import tk.djcrazy.MyCC98.adapter.InboxFragmentPagerAdapter;
import tk.djcrazy.libCC98.CachedCC98Service;
import tk.djcrazy.libCC98.NewCC98Service;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.astuetz.viewpager.extensions.PagerSlidingTabStrip;
import com.google.inject.Inject;
  
@ContentView(R.layout.activity_pm)
public class PmActivity extends BaseFragmentActivity implements OnPageChangeListener, TabListener{

	private static String TAG = "PmActivity";
	@Inject
	private NewCC98Service service;

	@InjectView(R.id.pm_main_pages)
	private ViewPager viewPager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
 		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pm);
		configureActionBar();
		InboxFragmentPagerAdapter adapter = new InboxFragmentPagerAdapter(
				getSupportFragmentManager());
		viewPager.setAdapter(adapter);
		viewPager.setOnPageChangeListener(this);
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(viewPager);
        tabs.setIndicatorColor(Color.parseColor("#1faeff"));

    }

	private void configureActionBar() {
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setLogo(new BitmapDrawable(getResources(), service.getCurrentUserAvatar()));
		actionBar.setTitle("论坛短消息");
	}

	@Override
	public boolean onOptionsItemSelected(
			com.actionbarsherlock.view.MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, HomeActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
			startActivity(intent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		viewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
	}

	@Override
	public void onPageScrollStateChanged(int arg0) {
		
	}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {
		
	}

	@Override
	public void onPageSelected(int arg0) {
		getSupportActionBar().setSelectedNavigationItem(arg0);
	}
}
