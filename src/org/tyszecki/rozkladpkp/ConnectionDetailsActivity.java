/*******************************************************************************
 * This file is part of the RozkladPKP project.
 * 
 *     RozkladPKP is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     RozkladPKP is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License 
 *     along with RozkladPKP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.tyszecki.rozkladpkp;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.tyszecki.rozkladpkp.ConnectionDetailsItem.PriceItem;
import org.tyszecki.rozkladpkp.PLN.Connection;
import org.tyszecki.rozkladpkp.PLN.ConnectionChange;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ConnectionDetailsActivity extends Activity {
	private ConnectionDetailsItemAdapter adapter;
	private PLN pln;
	//private String startDate;
	private int conidx;
	private static byte[] sBuffer = new byte[512];
	ProgressDialog priceProgress;
	Thread priceThread;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_details);
        setTitle("Plan podróży");
        
        //startDate = getIntent().getExtras().getString("StartDate");
        pln = new PLN(getIntent().getExtras().getByteArray("PLNData"));
        conidx = getIntent().getExtras().getInt("ConnectionIndex");
        
        adapter = new ConnectionDetailsItemAdapter(this, pln, conidx);
            
        ListView lv = (ListView)findViewById(R.id.connection_details);
        lv.setAdapter(this.adapter);
        
        lv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long id) {
				
				if(adapter.getItem(pos) instanceof PriceItem)
				{
					showPriceProgress();
					priceThread = new Thread(new Runnable(){
			            @Override
			            public void run() {
			                try {
			                	DefaultHttpClient client = new DefaultHttpClient();
			            		Connection c = pln.connections[conidx];
			                	
			                	String params;
			                	params = "start="+Integer.toString(c.getTrain(0).depstation.id);
			                	params += "&end="+Integer.toString(c.getTrain(c.getTrainCount()-1).arrstation.id);
			                	params += "&date="+getIntent().getExtras().getString("StartDate");
			                	params += "&time="+c.getTrain(0).deptime.toString();
			                	params += "&trains=";
			                	
			                	int j = c.getTrainCount();
			                	for(int i = 0; i < j; ++i)
			                	{
			                		params += c.getTrain(i).number.trim();
			                		if(i < j-1)
			                			params += ':';
			                	}
			                	
			                	Bundle extras = ConnectionDetailsActivity.this.getIntent().getExtras();
			                	params += "&REQ0JourneyProduct_prod_list_1="+extras.getString("Products");
			                	
			                	//ArrayList<SerializableNameValuePair> data = (ArrayList<SerializableNameValuePair>) extras.getSerializable("Attributes");
			                	
			                	//for(SerializableNameValuePair p : data)
			                	//	params += "&"+p.name+"="+p.value;
			                	
			                	params = params.replace(" ","%20");
			                	Log.i("RozkladPKP", params);
			            		HttpGet request = new HttpGet("http://2.cennikkolej.appspot.com/?"+params);
			            		
			                    HttpResponse response = client.execute(request);
			                     
			                    // Pull content stream from response
			                    HttpEntity entity = response.getEntity();
			                    InputStream inputStream = entity.getContent();
			                    final ByteArrayOutputStream content = new ByteArrayOutputStream();
			                    
			                    int readBytes = 0;
			                    while ((readBytes = inputStream.read(sBuffer)) != -1) {
			                        content.write(sBuffer, 0, readBytes);
			                    }
			                    
			                    final String[] spl = content.toString().split(":");
			                    
			                    runOnUiThread(new Runnable() {
									@Override
									public void run() {		
										hidePriceProgress();
										if(spl.length == 1)
											adapter.setPrice("-1", null, null);
										else
											adapter.setPrice(spl[0].trim(),spl[2].trim(),spl[1].trim());
									}
								});
			                	
							} catch (Exception e) {
								e.printStackTrace();
							}
			            }
					});
					priceThread.start();
				}
				else
				{
					Intent ni = new Intent(arg0.getContext(),TrainDetailsActivity.class);
					
					ni.putExtra("PLNData",pln.data);
					ni.putExtra("ConnectionIndex",conidx);
					ni.putExtra("TrainIndex", pos);
					ni.putExtra("StartDate",getIntent().getExtras().getString("StartDate"));
					startActivity(ni);
				}
			}
		});
        
	}
	void showPriceProgress()
	{
		priceProgress = ProgressDialog.show(ConnectionDetailsActivity.this, "Czekaj...", "Pobieranie informacji o cenie...",true,true,new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				if(priceThread != null && priceThread.isAlive())
				{
					priceThread.interrupt();
					dialog.dismiss();
				}
			}
		});
	}
	
	void hidePriceProgress()
	{
		if(priceProgress != null)
			priceProgress.dismiss();
	}
}
