package com.zoho.asset.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;

import com.source.util.DBConnection;
import com.zoho.asset.model.Asset;
import com.zoho.asset.model.Department;


public class DataManager {
    
    private DataManager() {
        initializeData();
    }

    private static class Holder {
        private static final DataManager instance = new DataManager();
    }

    public static DataManager getInstance() {
        return Holder.instance;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public List<Department> getDepartments() {
        return departments;
    }

    public void initializeData() {
        Connection conn = DBConnection.getConnection();
        Statement st = conn.createStatement() {
        ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM order_items");
    }
}
}
