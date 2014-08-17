package com.ane.pish_shomare;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ane.pish_shomare.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

class BackgroundTask extends AsyncTask <Void, Void, Void> {

	private ProgressDialog dialog;
	private MainActivity active; 

	private boolean noContact ;

	public BackgroundTask( MainActivity activity ) {
		dialog = new ProgressDialog(activity);
		active = activity;
		noContact = false ;
	}

	@Override
	protected void onPreExecute() {
		dialog.setMessage("در حال به‌روزرسانی. کمی صبر کنید...");
		dialog.show();
	}

	@Override
	protected void onPostExecute(Void result) {
		if (dialog.isShowing()) {
			dialog.dismiss();
		}

		String cntStr = "";
		try
		{
			cntStr = String.valueOf(MainActivity.ops.size());
		} catch( NumberFormatException e )
		{
			e.printStackTrace();
			return;
		}
		//Done Message
		String doneStr = cntStr + " شماره اصلاح شد.";
		if( noContact )
		{
			doneStr = "تغییری ایجاد نشد." ;
		}		
		active.tvDone.setText(doneStr);
		active.tvDone.setVisibility(View.VISIBLE);
	}

	@Override
	protected Void doInBackground(Void... params) {

		/////////// Explore The Contacts

		MainActivity.ops.clear();

		ContentResolver cr = active.getContentResolver();
		//		Cursor cur = cr.query(RawContacts.CONTENT_URI, null, null, null, null);
		Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
		if (cur.getCount() > 0) {
			while (cur.moveToNext()) {
				String id = cur.getString(cur.getColumnIndex(BaseColumns._ID));
				String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

				if (Integer.parseInt(cur.getString(cur
						.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
					Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[] { id },
							null);
					while (pCur.moveToNext()) {
						// Do something with phones
						String num = pCur.getString(pCur
								.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));


						// Check the changes
						String newNum = active.getNewNumber(num);
						if( newNum.replaceAll("\\D", "").equals(num.replaceAll("\\D", "")) )
						{
							//No Change
						}
						else
						{
							android.content.ContentProviderOperation.Builder builder 
							= ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI);

							builder.withSelection
							( ContactsContract.Data.CONTACT_ID + "=?" + " AND " 
									+ ContactsContract.Data.MIMETYPE + "=? AND "
									+ ContactsContract.CommonDataKinds.Phone.NUMBER + "= ?",
									new String[]{ String.valueOf(id), 
									ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
									String.valueOf(num)});

							builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNum);

							//							
							//							String phoneParams = ContactsContract.Data.CONTACT_ID + " = ?";// AND " + Data.MIMETYPE + " = ?";
							//							String[] phoneParamsWhere = new String[] {String.valueOf(id)};
							//
							//							android.content.ContentProviderOperation.Builder builder = ContentProviderOperation
							//									.newUpdate(ContactsContract.Data.CONTENT_URI);
							//					        MainActivity.ops.add(builder.withSelection(phoneParams, phoneParamsWhere)
							//									.withValue(Phone.NUMBER, newNum ).build());

							MainActivity.ops.add(builder.build());											

						}
					}
					pCur.close();
				}
			}
		}

		noContact = true ;
		if( ! MainActivity.ops.isEmpty() )
		{		

			// Execute all changes
			try {
				noContact = false ;
				active.getContentResolver().applyBatch(ContactsContract.AUTHORITY, MainActivity.ops);

			} catch (RemoteException e) {
				e.printStackTrace();
				if (dialog.isShowing()) {
					dialog.dismiss();
				}
				return null;
			} catch (OperationApplicationException e) {
				e.printStackTrace();
				if (dialog.isShowing()) {
					dialog.dismiss();
				}
				return null;
			} catch( Exception e )
			{
				e.printStackTrace();
				if (dialog.isShowing()) {
					dialog.dismiss();
				}
			}
		}
		else
		{
			// Manage "NO CONTACT ERROR" in postExecute
			noContact = true ;
		}

		if (dialog.isShowing()) {
			dialog.dismiss();
		}
		return null;
	}

}

public class MainActivity extends Activity {

	Button btConvert;
	Button btSingle;
	Button btCall;
	Button btAdd;

	TextView tvMessage;
	TextView tvDone;

	EditText etOld;
	EditText etNew;

	static int changCnt = 0 ;

	public static ProgressDialog verlauf;

	static Map<String,String> codesMap = new HashMap<String, String>();
	static ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

	Cursor cursor;
	ArrayList<String> vCard;
	String vfile;
	static Context mContext;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Parse.initialize(this, "ePsLZCkydTEGGuZtBST8BDG4fjYQYEb6lpGOONRF",
		// "4kw1VsjAK5KSJp5xoJbuVw1kkeoYzQIGo66fEFVi");
		// ParseAnalytics.trackAppOpened(getIntent());

		btConvert = (Button) findViewById(R.id.btConvert);
		btSingle = (Button) findViewById(R.id.btSingle);
		btCall = (Button) findViewById(R.id.btCall);
		btAdd = (Button) findViewById(R.id.btAdd);

		tvMessage = (TextView) findViewById(R.id.tvMessage);

		tvDone = (TextView) findViewById(R.id.tvDone);

		etOld = (EditText) findViewById(R.id.etOldPhone);
		etNew = (EditText) findViewById(R.id.etNewPhone);



		tvDone.setVisibility(View.INVISIBLE);
		btCall.setEnabled(false);
		btAdd.setEnabled(false);


		///////////////////// Read .CSF and Map
		AssetManager am = MainActivity.this.getAssets();
		InputStream is;
		try {
			is = am.open("enc.csv");
			InputStreamReader inputStreamReader = new InputStreamReader(is);
			BufferedReader f = new BufferedReader(inputStreamReader);
			String line = f.readLine();
			char[] chLine = line.toCharArray();
			while (line != null) {
				for( int i = 0 ; i < line.length() ; ++i  )
				{
					if( chLine[i] == '0' )
						chLine[i] = '2' ;
					if( chLine[i] == 'j' )
						chLine[i] = '0' ;
					if( chLine[i] == 'm' )
						chLine[i] = '1' ;
					if( chLine[i] == 'x' )
						chLine[i] = '3' ;
					if( chLine[i] == '/' )
						chLine[i] = '4' ;
					if( chLine[i] == 'F' )
						chLine[i] = '5' ;
					if( chLine[i] == '@' )
						chLine[i] = '6' ;
					if( chLine[i] == '_' )
						chLine[i] = '7' ;
					if( chLine[i] == '8' )
						chLine[i] = '9' ;
					if( chLine[i] == '%' )
						chLine[i] = '\n' ;
					if( chLine[i] == 'D' )
						chLine[i] = '+' ;
					if( chLine[i] == '>' )
						chLine[i] = ',' ;
					if( chLine[i] == '.' )
						chLine[i] = '8' ;
				}
				line = String.valueOf(chLine);

				String[] lines = line.split("\n");
				for (String l : lines) {			
					String[] tmpArray = l.split(",");

					codesMap.put("0"+tmpArray[0],tmpArray[1]) ;						
					codesMap.put("98"+tmpArray[0],tmpArray[1]) ;
					codesMap.put("0098"+tmpArray[0],tmpArray[1]) ;
				}


				line = f.readLine();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		/////////////////////

		etNew.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				if( s.toString().length() == 0 )
				{
					btCall.setEnabled(false);
					btAdd.setEnabled(false);
				}
				else
				{
					btCall.setEnabled(true);
					btAdd.setEnabled(true);
				}
			}

			public void beforeTextChanged(CharSequence s, int start,
					int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start,
					int before, int count) {				
			}
		});

		////////////////////

		btSingle.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				String num = etOld.getText().toString() ;
				String newNum = getNewNumber(num) ;
				if( num.replaceAll("\\D", "").startsWith("0")
						|| num.replaceAll("\\D", "").startsWith("98")
						|| num.replaceAll("\\D", "").startsWith("0098")
						|| num.startsWith("+98") )
				{
					if( newNum.replaceAll("\\D", "").equals(num.replaceAll("\\D", "")) )
					{
						//No Change
						new AlertDialog.Builder(MainActivity.this)
						.setTitle("بدون تغییر")
						.setMessage("شماره‌ی وارد شده طبق لیست مخابرات تغییری نمی‌کند.")
						.setPositiveButton("حله", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) { 
								// set focus on edit
							}
						})
						.show();
					}
					else
					{
						//Changed
						etNew.setText( newNum ) ;	
					}

				}
				else
				{
					//should start with 0
					new AlertDialog.Builder(MainActivity.this)
					.setTitle("شماره نامعتبر")
					.setMessage("شماره‌ی صحیح باید با ۰ یا ۹۸+ یا ۰۰۹۸ شروع شود.")
					.setPositiveButton("حله", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) { 
							// set focus on edit
						}
					})
					.show();
				}
			}
		} );

		/////////////

		btCall.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				String uri = "tel:" + etNew.getText().toString().trim() ;
				Intent intent = new Intent(Intent.ACTION_DIAL);
				intent.setData(Uri.parse(uri));
				startActivity(intent);
			}
		});

		/////////////////////////

		btAdd.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				Intent intent = new Intent(Intent.ACTION_INSERT);
				intent.setType(ContactsContract.Contacts.CONTENT_TYPE);

				intent.putExtra(ContactsContract.Intents.Insert.PHONE, etNew.getText().toString().trim() );
				intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, Phone.TYPE_HOME );

				MainActivity.this.startActivity(intent);				
			}
		});

		/////////////////////////

		btConvert.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				BackgroundTask task = new BackgroundTask(MainActivity.this);
				task.execute();
			}

			private void sendToParse() {
				Map<String, String> dimensions = new HashMap<String, String>();
				// What type of news is this?
				dimensions.put("button", "convert");
				// Send the dimensions to Parse along with the 'read' event

				// ParseAnalytics.trackEvent("click", dimensions);

			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public String getNewNumber( String oldNum )
	{
		// Remove Non-DIGITS
		String num = oldNum;
		num = num.replaceAll("\\D", "");

		if( num.length() <= 7 )
		{
			return oldNum ;
		}

		/// Khorasan - Alborz - Qom
		for( int len = 4 ; len <= 14 ; ++len )
		{
			if( len > num.length()-1 )
			{
				break;
			}
			String key = num.substring(0,len);
			if( codesMap.containsKey(key) )
			{
				String val = codesMap.get(key);
				num = num.replaceFirst(key, val);
				return num;
			}
		}

		if( num.equals(oldNum.replaceAll("\\D", "")) )
		{
			return oldNum;
		}

		return num;
	}
}
