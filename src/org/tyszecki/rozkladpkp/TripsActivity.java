package org.tyszecki.rozkladpkp;

import org.tyszecki.rozkladpkp.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class TripsActivity extends Activity {
      
	private TimeButton timeb;
	private DateButton dateb;
	private ProductsButton prodb;
	private SharedPreferences pref;
	
	private StationEdit depEdit,arrEdit;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trips);
        
        initializeGui();
    }

	private void initializeGui() {
		
		timeb	= (TimeButton) findViewById(R.id.tripsTimeBut);
        timeb.setToNow();
        timeb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				showDialog(0);
			}
		});
        
        dateb = (DateButton) findViewById(R.id.tripsDateBut);
        dateb.setToNow();
        dateb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				showDialog(1);
			}
		});
        
        pref = getPreferences(MODE_PRIVATE);
        prodb = (ProductsButton) findViewById(R.id.tripsProdBut);
        prodb.setProductString(pref.getString("Products", "11110001111111"));
        prodb.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				showDialog(2);
				
			}
		});
 
        depEdit = (StationEdit) findViewById(R.id.tripsDepEdit);
        arrEdit = (StationEdit) findViewById(R.id.tripsArrEdit);
        
        depEdit.setHint("Stacja początkowa");
        arrEdit.setHint("Stacja docelowa");
        
        depEdit.setAutoComplete(pref.getBoolean("EnableStationAC", true));
        arrEdit.setAutoComplete(pref.getBoolean("EnableStationAC", true));
        
        Button ok	= (Button) findViewById(R.id.tripsOK);
        ok.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				if(depEdit.getText().toString().trim().length() == 0)
				{
					Toast.makeText(getApplicationContext(), "Wprowadź nazwę stacji początkowej", Toast.LENGTH_SHORT).show();
					return;
				}
				else if(arrEdit.getText().toString().trim().length() == 0)
				{
					Toast.makeText(getApplicationContext(), "Wprowadź nazwę stacji docelowej", Toast.LENGTH_SHORT).show();
					return;
				}
				
				String sidd = depEdit.getCurrentSID();
				String sida = arrEdit.getCurrentSID();
				
				Intent ni = null;
				if(sidd.equals("") || sida.equals(""))
				{
					ni = new Intent(arg0.getContext(),Trips2Activity.class);
					if(!sidd.equals(""))
						ni.putExtra("depSID", sidd);
						
					ni.putExtra("depText", depEdit.getText().toString());
						
					
					if(!sida.equals(""))
						ni.putExtra("arrSID", sida);
				
					ni.putExtra("arrText", arrEdit.getText().toString());
				}
				else
				{
					ni = new Intent(arg0.getContext(),ConnectionsActivity.class);
					ni.putExtra("ZID", arrEdit.getCurrentSID());
					ni.putExtra("SID", depEdit.getCurrentSID());
				}
				ni.putExtra("Time", timeb.getTime());
				ni.putExtra("Date", dateb.getDate());
				ni.putExtra("Products", prodb.getProductString());
				startActivity(ni);
			}
		});
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
	    switch (id) {
	    case 0:
	        return timeb.timeDialog();
	    case 1:
	    	return dateb.dateDialog();
	    case 2:
	    	return prodb.getDialog();
	    }
	    return null;
	}
}
