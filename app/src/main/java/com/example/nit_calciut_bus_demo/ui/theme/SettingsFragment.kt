package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.material.tabs.TabLayout

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup TabLayout with horizontal tabs (click to load content)
        val tabLayout = view.findViewById<TabLayout>(R.id.settingsTabLayout)
    tabLayout.addTab(tabLayout.newTab().setText("College"))
    tabLayout.addTab(tabLayout.newTab().setText("User Info"))
    tabLayout.addTab(tabLayout.newTab().setText("About"))

        // If first time opening, show College tab content so the screen isn't blank
        if (savedInstanceState == null) {
            tabLayout.getTabAt(0)?.select()
            replaceContent(CollegeConfigFragment())
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> replaceContent(CollegeConfigFragment())
                    1 -> replaceContent(UserInfoFragment())
                    2 -> replaceContent(PlaceholderFragment.newInstance(
                        title = "About",
                        message = "About details will be available soon"
                    ))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                // Ensure content is visible even if the first tab was already selected
                onTabSelected(tab)
            }
        })
    }

    private fun replaceContent(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.settingsContentContainer, fragment)
            .commit()
    }
}
