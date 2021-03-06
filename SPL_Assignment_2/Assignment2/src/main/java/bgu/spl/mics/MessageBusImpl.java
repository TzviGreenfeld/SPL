package bgu.spl.mics;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link MessageBusImpl class is the implementation of the MessageBus interface.
 * Write your implementation here!
 * Only private fields and methods can be added to this class.
 */
public class MessageBusImpl implements MessageBus {

	private static class MessageBusSingletonHolder{
		//this is thread-safe singleton
		private static MessageBusImpl messageBusInstance = new MessageBusImpl();
	}
	private ConcurrentHashMap<MicroService,Queue<Message>> hashMapmessages;
	private ConcurrentHashMap<Class<?>,Queue<MicroService>> hashMapofmicroservices;
	private ConcurrentHashMap<Event,Future> hashMapfuture;


	private MessageBusImpl(){
		hashMapmessages = new ConcurrentHashMap<>();
		hashMapofmicroservices = new ConcurrentHashMap<>();
		hashMapfuture= new ConcurrentHashMap<>();
	}

	public static MessageBusImpl getInstance(){
		return MessageBusSingletonHolder.messageBusInstance;
	}
	//The Getters are for tests
	protected ConcurrentHashMap<MicroService, Queue<Message>> getHashMapmessages() {
		return hashMapmessages;
	}

	protected ConcurrentHashMap<Event, Future> getHashMapfuture() {
		return hashMapfuture;
	}

	protected ConcurrentHashMap<Class<?>, Queue<MicroService>> getHashMapofmicroservices() {
		return hashMapofmicroservices;
	}

	@Override
	public <T> void subscribeEvent(Class<? extends Event<T>> type, MicroService m) {
		Queue<MicroService> q = new LinkedList<>();
		synchronized (hashMapofmicroservices) {
			if (hashMapofmicroservices.containsKey(type)) {
				q = hashMapofmicroservices.get(type);
				q.add(m);
			} else {
				q.add(m);
				hashMapofmicroservices.put(type, q);
			}
			hashMapofmicroservices.notifyAll();
		}
	}

	@Override
	public void subscribeBroadcast(Class<? extends Broadcast> type, MicroService m) {
		Queue<MicroService> q = new LinkedList<>();
		synchronized (hashMapofmicroservices) {
			if (hashMapofmicroservices.containsKey(type)) {
				q = hashMapofmicroservices.get(type);
				q.add(m);
			} else {
				q.add(m);
				hashMapofmicroservices.put(type, q);
			}
			hashMapofmicroservices.notifyAll();
		}
	}
	@Override @SuppressWarnings("unchecked")
	public <T> void complete(Event<T> e, T result) {
		Future<T> future = hashMapfuture.get(e);
		future.resolve(result);

	}

	@Override
	public void sendBroadcast(Broadcast b) {
		synchronized (hashMapmessages) {
			for (Class c : hashMapofmicroservices.keySet()) {
				if (c == b.getClass()) {
					for (MicroService m : hashMapofmicroservices.get(c)) {
						hashMapmessages.get(m).add(b);
					}
				}
			}
			hashMapmessages.notifyAll();
		}
	}


	@Override
	public <T> Future<T> sendEvent(Event<T> e) {
		Future<T> future = new Future<T>();
		synchronized (hashMapmessages) {
			for (Class c : hashMapofmicroservices.keySet()) {
				if (c == e.getClass()) {
					MicroService m = hashMapofmicroservices.get(c).poll();
					hashMapmessages.get(m).add(e);
					hashMapofmicroservices.get(c).add(m);
				}
			}
			hashMapfuture.put(e, future);
			hashMapmessages.notifyAll();
		}
		return future;
	}

	@Override
	public void register(MicroService m) {
		if(!hashMapmessages.contains(m)) {
			Queue<Message> qOfMicroservice = new LinkedList<>();
			hashMapmessages.put(m, qOfMicroservice);
		}
	}

	@Override
	public void unregister(MicroService m) {
		synchronized (hashMapmessages) {
			if (hashMapmessages.containsKey(m)) {
				hashMapmessages.remove(m);
				for (Queue<MicroService> q : hashMapofmicroservices.values()) {
					if (q.contains(m)) {
						q.remove(m);
					}
				}
			}
			hashMapmessages.notifyAll();
		}
	}

	@Override
	public Message awaitMessage(MicroService m) throws InterruptedException {
		synchronized (hashMapmessages) {
			Queue<? extends Message> q = hashMapmessages.get(m);
			while (q.isEmpty()) {
				try {
					hashMapmessages.wait();
					q = hashMapmessages.get(m);

				} catch (InterruptedException e) {

				}
			}
			Message message = q.poll();

			return message;
		}
	}
}

