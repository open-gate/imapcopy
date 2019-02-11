package biz.opengate.imapCopy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TimeStatistic {
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	DEFINITION

	private static final Logger logger = LogManager.getLogger();
	
	private String name;
	private long sum=0;
	private long count=0;
	private long startTime;
	private long lastSignal=0;
	
	public TimeStatistic(String name) {
		this.name=name;
	}
	

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	//	UTILITIES
	
	public void start() {
		startTime=System.currentTimeMillis();
		if (lastSignal==0) lastSignal=startTime;
	}
	
	public void stop() {
		stop(1);
	}
	
	public void stop(final int divisor) {
		final long now=System.currentTimeMillis();
		final long delta=now-startTime;
		
		if (delta>0) {
			sum+=delta;
			count+=divisor;
		}		
		
		if (now-lastSignal>5*1000) {
			lastSignal=now;
			log();
		}
	}
	
	private void log() {				
		long average=(long) (sum/((double)count));
		logger.info("[TimeStatistic]["+name+"]["+average+"]"); 
	}
}
