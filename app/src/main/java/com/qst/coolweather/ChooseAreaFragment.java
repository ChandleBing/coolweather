package com.qst.coolweather;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qst.coolweather.db.City;
import com.qst.coolweather.db.County;
import com.qst.coolweather.db.Province;
import com.qst.coolweather.util.HttpUtil;
import com.qst.coolweather.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

// 编写用于遍历省市县数据的碎片
public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;                         // 标题内容
    private Button backButton;                          // 返回上级菜单按钮
    private ListView listView;                          // 省市县的数据显示在listView中
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();
    // 省列表
    private List<Province> provinceList;
    // 市列表
    private List<City> cityList;
    // 县列表
    private List<County> countyList;
    // 选中的省份
    private Province selectedProvince;
    // 选中的城市
    private City selectedCity;
    // 当前选中的级别
    private int currentLevel;

    /**
     * 获得titleText、backButton、listView等控件的实例
     * 初始化ArrayAdapter，并将它设置为ListView的适配器
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(adapter);
        return view;
    }

    /**
     * 给ListView和Button设置点击事件，通过对当前ListView的列表等级的判断，做出相应的选择
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();       // 加载市级数据
                }
                else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();     // 加载县级数据
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    queryCities();           // 加载市级数据
                }
                else if (currentLevel == LEVEL_CITY) {
                    queryProvinces();        // 加载省级数据
                }
            }
        });
        queryProvinces();
    }

    /**
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryProvinces() {
        titleText.setText("中国");            // 将头布局的标题设置成“中国”
        backButton.setVisibility(View.GONE);  // 将返回按钮隐藏起来
        // 调用LitePal的查询接口来从数据库中读取省级数据
        provinceList = DataSupport.findAll(Province.class);
        // 若读取到了数据，即列表不为空，则直接将数据显示到界面上
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }
        // 如果没有读取到数据，则组装出一个请求地址address
        else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");         // 调用queryFromServer()方法来从服务器上查询数据
        }
    }

    /**
     * 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());    // 将头布局的标题设置为当前选中的城市所属的省份
        backButton.setVisibility(View.VISIBLE);                    // 不再隐藏按钮，按钮可见
        // 调用LitePal的查询接口来从数据库中读取市级数据
        cityList = DataSupport.where("provinceid = ?", String.valueOf(selectedProvince.getId())).find(City.class);
        // 若读取到了数据，即列表不为空，则直接将数据显示到界面上
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }
        // 如果没有读取到数据，则组装出一个请求地址address
        else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address, "city");
        }
    }

    /**
     * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询
     */
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());    // 将头布局的标题设置为当前选中的县级所属的市级
        backButton.setVisibility(View.VISIBLE);            // 不再隐藏按钮，按钮可见
        // 调用LitePal的查询接口来从数据库中读取县级数据
        countyList = DataSupport.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        // 若读取到了数据，即列表不为空，则直接将数据显示到界面上
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        }
        // 如果没有读取到数据，则组装出一个请求地址address
        else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     * @param address
     * @param type
     */
    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        // 调用HttpUtil的sendOkHttpRequest()方法来向服务器发送请求，响应的数据会回调到onResponse()方法中
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 通过runOnUiThread()方法回到主线程处理逻辑
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 调用Utility中的三个方法来解析和处理服务器返回的数据，并存储到数据库中
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);
                }
                else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                }
                else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                }
                // 再次调用三个query方法来重新加载数据，借助runOnUiThread()方法来实现从子线程切换到主线程
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            }
                            else if ("city".equals(type)) {
                                queryCities();
                            }
                            else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

}
