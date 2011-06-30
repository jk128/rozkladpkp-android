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

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;

import org.tyszecki.rozkladpkp.ExternalDelayFetcher.ExternalDelayFetcherCallback;

import android.util.Log;

public class PLN {
	
	public static final int DATE_GENERATED = 0;
	public static final int DATE_START = 1;
	public static final int DATE_END = 2;
	
	
	final int ConnectionOffset = 0x4a;
	final int ConnectionSize = 12;
	int attributesStart,attributesEnd;
	int stationsStart;
	int stringStart,availabilitiesStart;

	
	final int TrainSize = 20;
	final int StationSize = 14;
	
	
	private StringManager strings;
	private AttributeManager attributes;
	
	public Connection[] connections;
	private Station[] stations;
	private Station dep,arr; 
	private int totalConCnt = -1;
	private int actualDaysCount = -1;
	private HashMap<Integer,Availability> availabilities;
	private HashMap<String,Integer> exDelays = new HashMap<String, Integer>();
	
	public int conCnt;
	private int tripCount = 0;
	Boolean delayInfo = null;

	byte[] data;
	private android.text.format.Time sdate,edate,today;
	
	class StringManager{
		HashMap<Integer,String> cache = new HashMap<Integer, String>();
		
		String get(int offset)
		{
			if(cache.containsKey(offset))
				return cache.get(offset);
			
			String t = getString(offset+stringStart);
			cache.put(offset, t);
			return t;
		}
	}
	
	class AttributeManager{
		private HashMap<Integer,String[]> cache = new HashMap<Integer, String[]>();
		
		String[] get(int offset)
		{
			if(cache.containsKey(offset))
				return cache.get(offset);
			
			String[] t = readAttributeList(offset);
			cache.put(offset, t);
			return t;
		}
	}
	
	class Time{
		int val;
		int days;
		public Time(int v) {
			val = v;
			days = val/2400;
			val %= 2400;
		}
		
		public void normalize() {
			if(val % 100 > 59)
			{
				val += 100;
				val -= 60;
				
				if(val > 2400)
				{
					days++;
					val -= 2400;
				}
			}
		}
		
		public String toString() {
			String t   = Integer.toString(val);
			while(t.length() < 4)
				t = '0'+t;
			return t.substring(0, 2)+":"+t.substring(2);
		}
		
		public String toLongString() {
			return ((days>0)?(Integer.toString(days)+" dni "):"")+toString();
		}
		
		public int intValue()
		{
			return val;
		}
		
		public Time difference(Time b)
		{
			int va = val+days*2400;
			int diff = va-(b.val+b.days*2400);
			
			if(diff%100 >= 60)
				diff -= 40;
			
			return new Time(diff);		
		}
	}
	
	class Availability{
		private int length;
		private int card = 0;
		public Availability(int m,int offset, int dayOffset, int len) {
			msgOffset = m;
			dOffset = dayOffset;
			days	= new BitSet(len*8);
			int ix = 0;
			
			for(int i = 0; i < len; i++)
				for(int j = 7; j >= 0; j--, ix++)
					if(((data[offset+i] >>j) & 1) != 0)
					{
						days.set(ix);
						++card;
					}
			
			length = len*8;
		}
		
		public boolean available(int day)
		{
			day -= dOffset*8;
			if(day >= 0 && day < length)
				return days.get(day);
			return false;
		}
		
		public int length()
		{
			return dOffset*8+days.size();
		}
		
		public int daysCount()
		{
			return card;
		}
		
		public BitSet bitset()
		{
			return days;
		}
		
		public int offset()
		{
			return dOffset;
		}
		
		private String getMessage()
		{
			if(msg == null)
				msg = strings.get(msgOffset);
			return msg;
		}
		private String msg;
		private BitSet days;
		private int dOffset,msgOffset;
	}
	
	public class Station {
		int x,y,id;
		String name;
		
		public String toString()
		{
			return name;
		}
	}
	
	public class Train {
		Time deptime,arrtime;
		String number;
		Station depstation,arrstation;
		private String attr[] = null;
		
		private int changeOffset = -1, attributesOffset;
		private TrainChange change;
		
		public TrainChange getChange()
		{
			if(changeOffset == -1 && !exDelays.containsKey(number))
				return null;
			if(change != null)
				return change;
			
			if(changeOffset != -1)
				change = readTrainChanges(changeOffset);
			if(change == null && exDelays.containsKey(number))
			{
				change = new TrainChange();
				
				int arr = arrtime.intValue();
				int mindel = exDelays.get(number);
				int hrs = mindel/60; 
				arr += hrs*100;
				mindel -= hrs*60;
				arr += mindel;
				
				change.realarrtime = new Time(arr);
				change.realarrtime.normalize();
				
				int dep = deptime.intValue();
				dep += hrs*100;
				dep += mindel;
				
				change.realdeptime = new Time(dep);
				change.realdeptime.normalize();
			}
			return change;
		}
		
		public int getAttributeCount()
		{
			if(attr == null)
				attr = attributes.get(attributesOffset);
			return attr.length;
		}
		
		public String getAttribute(int index)
		{
			if(attr == null)
				attr = attributes.get(attributesOffset);
			return attr[index];
		}
	}
	
	public class Message {
		Station start,end;
		String brief,full;
	}
	
	public class TrainChange {
		Time realdeptime,realarrtime;
		String realdepplatform,realarrplatform;
	}
	
	public class ConnectionChange {
		int departureDelay;
	}
	
	public class Connection {
		public int changes,trOffset;
		
		private int timeOffset, trainsOffset, trainCount, changeOffset = -1;
		
		private Time journeyTime = null;
		private Train[] trains = null;
		
		Message[] messages;
		Availability availability;
		private ConnectionChange change;
		
		public Time getJourneyTime()
		{
			if(journeyTime == null)
				journeyTime = new Time(readint(timeOffset));
			return journeyTime;
		}
		
		public int getTrainCount()
		{
			return trainCount;
		}
		
		public Train getTrain(int index)
		{
			if(trains == null)
				trains = new Train[trainCount];
			else if(trains[index] != null)
				return trains[index];
			
			Train t = readTrain(trainsOffset + index*TrainSize);
			trains[index] = t;
			
			if(changeOffset != -1)
				t.changeOffset = changeOffset+8*(index+1);
			
			return t;
		}
		
		public ConnectionChange getChange()
		{
			if(changeOffset == -1 && !exDelays.containsKey(getTrain(0).number))
				return null;
			if(change != null)
				return change;
			
			if(changeOffset != -1)
				change = readConnectionChanges(changeOffset);
			if(change == null && exDelays.containsKey(getTrain(0).number))
			{
				Log.i("RozkladPKP","Czytam exdelay");
				change = new ConnectionChange();
				Train t = getTrain(0);
				change.departureDelay = t.getChange().realdeptime.difference(t.deptime).intValue(); 
			}
			return change;
		}
	}
	
	public class Trip {
		public Trip(Connection connection, int idx, String d) {
			con = connection;
			date = d; 
			conidx = idx;
		}
		String date;
		Connection con;
		//FIXME: Wymyśleć, jak zastąpić to, żeby było ładnie.
		int conidx;
	}
	
	public class TripIterator implements Iterator<Trip>{
		int pos = 0;
		int max = -1;
		int dix = 0, ldix = 0;
		int tripsLeft = tripCount;
		android.text.format.Time time = new android.text.format.Time(sdate);
		
		public TripIterator()
		{
			time.allDay = true;
			time.normalize(false);
			
			for(int i = 0; i < conCnt; ++i)
				if(connections[i].availability.length() > max)
					max = connections[i].availability.length();
		}
		
		private boolean move(boolean forward)
		{
			//Nie ma żadnych połączeń
			if(conCnt == 0) 
				return false;
			
			if(!forward)
			{
				--pos;
				tripsLeft++;
			}
			else if(tripsLeft == 0)
				return false;
			
			int cix = pos%conCnt;
			dix = pos/conCnt;
			
			while(dix <= max)
			{
				if(connections[cix].availability != null && connections[cix].availability.available(dix))
					return true;
				else
				{
					if(forward)
						pos++;
					else 
						pos--;
					
					cix = pos%conCnt;
					dix	= pos/conCnt;
				}
			}
			
			return false;
		}
		
		@Override
		public boolean hasNext() {
			return move(true);
		}

		@Override
		public Trip next() {
			if(hasNext())
			{
				int cix = pos%conCnt;
				
				if(ldix != dix)
				{
					time.monthDay -= (ldix-dix);
					time.normalize(false);
					ldix = dix;
				}
				
				pos++;
				tripsLeft--;
				return new Trip(connections[cix], cix, time.format("%d.%m.%Y"));
			}
			else
				return null;
		}

		public boolean advance(){
			pos++;
			return hasNext();
		}
		
		public boolean back(){
			return move(false);
		}
		public void moveToLast(){
			//Przejdz do ostatniego połączenia
			while(advance());
			//Cofnij o jedno
			back();
		}
		@Override
		public void remove() {			
		}
	}
	
	public PLN(byte[] byte_data) {
		data = byte_data;
		strings = new StringManager();
		attributes = new AttributeManager();
		
		stationsStart	= readint(0x36);
		attributesStart	= readint(0x3a);
		attributesEnd	= readint(0x3e);
		conCnt			= readint(0x1e);
		
		setupDates();
		readStringTable();
		readStations();
		readHeaderStations();
		readAvailabilities();
		readConnections();
		Log.i("RozkladPKP", Integer.toString(tripCount));
	}

	public TripIterator tripIterator(){
		return new TripIterator();
	}
	
	public Station departureStation(){
		return dep;
	}

	public Station arrivalStation(){
		return arr;
	}
	
	public String id()
	{
		return strings.get(10);
	}
	
	public int connectionCount()
	{
		if(totalConCnt == -1)
		{
			++totalConCnt;
			for(Connection c : connections)
				totalConCnt += c.availability.daysCount();
		}
		return totalConCnt;
	}
	
	public int daysCount()
	{
		if(actualDaysCount == -1)
		{
			BitSet res = new BitSet();
			for(Connection c : connections)
			{
				Availability a = c.availability;
				int j = a.dOffset*8;
				BitSet t = a.bitset();
				
				for(int i = 0; i < t.length(); i++,j++)
					res.set(j,t.get(i));
				
			}
			actualDaysCount = res.cardinality();
		}
		return actualDaysCount;
	}
	
	private int readint(int pos)
	{
		int r =  (int) (data[pos] & 0x000000FF);
		r += ((int) (data[pos+1] & 0x000000FF))*256;
		
		return r;
	}
	
	//In theory, this method should return 'long', but IDs and gps coordinates never go so big
	
	private int readLong(int pos)
	{
		int r =  (int) (data[pos] & 0x000000FF);
		r += ((int) (data[pos+1] & 0x000000FF))*256;
		r += ((int) (data[pos+2] & 0x000000FF))*65536;
		r += ((int) (data[pos+3] & 0x000000FF))*16777216;
		
		return r;
	}
	
	
	private String getString(int pos)
	{
		int start = pos; 
			
		while(data[pos] != 0)
			pos++;
		
		byte[] b = new byte[pos-start];
		
		pos = start;
		start = 0;
		
		while(data[pos] != 0)
			b[start++] = data[pos++];
		
		try {
			return new String(b,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
	
	private Connection readConnection(int pos)
	{
		Connection ret = new Connection();
		int tcnt	= readint(pos+6);
		int toff	= readint(pos+2);
		ret.changes	= readint(pos+8);
		ret.timeOffset	= pos+10;
		ret.trainsOffset = ConnectionOffset+toff;
		ret.trainCount = tcnt;
		
		ret.availability = availabilities.get(readint(pos));
		tripCount += ret.availability.daysCount();
		return ret;
	}
	
	private void setupDates()
	{
		sdate = new android.text.format.Time();
		sdate.year = 1979;
		sdate.month = 11;
		sdate.monthDay = 31;
		
		edate = new android.text.format.Time(sdate);
		today = new android.text.format.Time(sdate);
		
		sdate.monthDay += readint(0x28);
		edate.monthDay += readint(0x2a);
		today.monthDay += readint(0x2c);
		
		sdate.normalize(false);
		edate.normalize(false);
		today.normalize(false);
	}
	
	private Train readTrain(int pos)
	{
		Train r = new Train();
		
		r.deptime	= new Time(readint(pos));
		r.depstation = stations[readint(pos+2)];
		r.arrtime	= new Time(readint(pos+4));
		r.arrstation = stations[readint(pos+6)];
		r.number	= strings.get(readint(pos+10));
		
		r.attributesOffset = readint(pos+18);
		
		return r;
	}



	private void readStringTable()
	{
		stringStart = readint(0x24);
		availabilitiesStart	  = readint(0x20);
	}
	
	private void readHeaderStations()
	{
		dep = readHeaderStation(2);
		arr = readHeaderStation(0x10);
	}
	
	private Station readHeaderStation(int offset)
	{
		int nameLoc = readint(offset);
		if(nameLoc == 0)
			return null;
		
		Station ret = new Station();
		
		ret.name = strings.get(nameLoc);
		ret.x = readLong(offset+6);
		ret.y = readLong(offset+10);
		
		return ret;
	}
	
	private void readStations() {
			
		stations = new Station[(attributesStart-stationsStart)/StationSize];
	
		for(int i = 0, pos = stationsStart; pos < attributesStart; pos += StationSize, i++)
		{
			stations[i] = new Station();
			stations[i].name = strings.get(readint(pos));
			stations[i].id	= readLong(pos+2);
			stations[i].x	= readLong(pos+6);
			stations[i].y	= readLong(pos+10);
		}
	}
	
	private void readAvailabilities() {
		
		availabilities	= new HashMap<Integer, Availability>();
		
		for(int i = 0, pos = availabilitiesStart; pos < stationsStart; i++, pos += 6)
		{
			int len = readint(pos+4);
			int dOffset = readint(pos+2);
			availabilities.put(pos-availabilitiesStart, new Availability(readint(pos),pos+6,dOffset,len));
			
			pos += len;
		}
	}
	
	private String[] readAttributeList(int offset) {
		int pos = offset + attributesStart;
		
		int cnt = readint(pos);
		String[] tab = new String[cnt];

		int p = pos+2;
		for(int i = 0; i < cnt; i++,p+=2)
			tab[i] = strings.get(readint(p));

		return tab;
	}
	
	private void readMessages() {
		int pos = readint(attributesEnd+0x16);
		//TODO: investigate how these messages are connected with trains
		//No messages
		if(pos == 0)
			return;
	}

	private void readConnections() {
		
		connections = new Connection[conCnt];
		
		int chinfo = readint(attributesEnd+0xe);
		boolean hasChanges = (chinfo != 0);
		if(hasChanges)
			chinfo += conCnt*2+4;
		
		for(int i = 0; i < conCnt; i++)
		{
			connections[i] = readConnection(ConnectionOffset+ConnectionSize*i);
			if(hasChanges)
			{
				connections[i].changeOffset = chinfo;
				chinfo += (connections[i].trainCount+1)*8;
			}
		}
	}
	
	private TrainChange readTrainChanges(int offset) {
		
		int rd = readint(offset);
		int ra = readint(offset+2);
		int rdp = readint(offset+4);
		int rap = readint(offset+6);
		
		if(ra == 0xffff && rd == 0xffff && rdp == 0 && rap == 0)
			return null;
		
		delayInfo = true;
		
		TrainChange tc = new TrainChange();
		tc.realdeptime = (rd == 0xffff) ? null : new Time(rd);
		tc.realarrtime = (ra == 0xffff) ? null : new Time(ra);
		tc.realdepplatform = (rdp == 0) ? null : strings.get(rdp);
		tc.realarrplatform = (rap == 0) ? null : strings.get(rap);
		
		return tc;
	}

	private ConnectionChange readConnectionChanges(int offset) {
		int delay = readint(offset+2);
		if(delay == 255)
			return null;
		
		ConnectionChange ch = new ConnectionChange();
		ch.departureDelay = delay;	
	
		delayInfo = true;
		
		return ch;
	}

	boolean hasDelayInfo()
	{
		if(delayInfo == null)
		{
			int chinfo = readint(attributesEnd+0xe);
			if(chinfo == 0)
				delayInfo = false;
			else
				delayInfo = hasDelay(chinfo + conCnt*2+4);
		}
		if((delayInfo == null || !delayInfo) && !exDelays.isEmpty())
		{
			//FIXME: Nie trzeba wszystkiego odczytywać.
			for(int i = 0; i < conCnt; i++)
			{
				Connection c = connections[i];
				for(int j = 0; j < c.getTrainCount(); ++j)
					if(exDelays.containsKey(c.getTrain(j).number))
					{
						delayInfo = true;
						return true;
					}
			}
			delayInfo = false;
		}
		return delayInfo;
	}
	
	boolean hasDelay(int p)
	{
		//0,0,xff,0,xff,xff,xff,xff <- puste polaczenie
		//xff,xff,xff,xff,0,0,0,0 <- pusty pociag
		for(int i = 0; i < conCnt; ++i)
		{
			if(data[p++] != 0) return true;
			if(data[p++] != 0) return true;
			if(data[p++] != -1) return true;
			if(data[p++] != 0) return true;
			
			for(int k = 0; k < 4; ++k)
				if(data[p++] != -1) return true;
			
			for(int j = 0; j < connections[i].getTrainCount(); ++j)
			{
				for(int k = 0; k < 4; ++k)
					if(data[p++] != -1) return true;
				for(int k = 0; k < 4; ++k)
					if(data[p++] != 0) return true;
			}
		}	
		return false;
	}
	
	public void addExternalDelayInfo(HashMap<String,Integer> delays)
	{
		exDelays.putAll(delays);
	}
}
