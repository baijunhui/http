# http
AsyncTask源码分析及OkHttp分析，封装使用


	AsyncTask是android之前一直使用的异步下载，虽然他现在已经不更新了，但是还是很有研究价值的，我们来研究下它的工作原理
OkHttp是现在http请求很火的一个三方库，我们写了一个demo，对他进行了封装，但是封装的太彻底，因为每个应用用法的返回的数据是不一样，所以最好是对返回的string进行处理，我这边是对他进行了封装，使用Gson进行了解析，转换成了我们常用的对象。


	android UI线程是不安全的，所以不能在其他线程中更新UI，只能使用消息机制通知及handler消息机制，android官方提供了一个AsyncTask来更新UI，我们来看看他是怎么实现的，我们先来看看使用方法

	AsyncTask是抽象的，所以只能交给其子类来实现，里面有三个参数的类型，分别是传入的值的类型，更新中进度显示，可以设置为void，就是不现实，还有就是返回值的类型了，我们来看看其源码

	public abstract class AsyncTask<Params, Progress, Result> {
	}

	/**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     */
    public AsyncTask() {
        mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);

                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                //noinspection unchecked
                Result result = doInBackground(mParams);
                Binder.flushPendingCommands();
                return postResult(result);
            }
        };

        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {
                try {
                    postResultIfNotInvoked(get());
                } catch (InterruptedException e) {
                    android.util.Log.w(LOG_TAG, e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("An error occurred while executing doInBackground()",
                            e.getCause());
                } catch (CancellationException e) {
                    postResultIfNotInvoked(null);
                }
            }
        };
    }

	这个里面有三个方法
	onPreExecute()开始执行之前的
	doInBackground()实现逻辑就在这个里面实现的，这个里的代码是在子线程中完成的
	onPostExecute()执行结果在这个里面的
	onCancel()取消执行的
	onProgressUpdate()执行更新进度显示的，里面有我们要显示的进度，这个在ui线程中可以直接调用
	publishProgress()更新进度的，在doInBackground方法里调用可以更新到onProgressUpdate方法里的

	最后执行execute方法开始这个异步的工作。我们从execute方法来看下里面的实现逻辑
	execute(Params... params)
	executeOnExecutor(Executor exec, Params... params)
	从这两个方法我们可以看出，这个只是一个自己定义Executeor,一个是使用默认的

1、	我们从上面那个方法开始看
	@MainThread
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);
    }
    还是执行到了下面那个方法，只是传了一个默认值

2、	mStatus默认是PENDING状态，所有执行下面的，
	@MainThread
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec,
            Params... params) {
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
                case RUNNING:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task is already running.");
                case FINISHED:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task has already been executed "
                            + "(a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;

        //回调到onPreExecute方法
        onPreExecute();

        mWorker.mParams = params;
        //执行了SerialExecutor的execute方法，并且把mFuture作为参数传进去了
        exec.execute(mFuture);

        return this;
    }

3、
	private static class SerialExecutor implements Executor {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        scheduleNext();
                    }
                }
            });
            if (mActive == null) {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext() {
            if ((mActive = mTasks.poll()) != null) {
                THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }

4、下面我们看下FutureTask这个类里的run方法，通过查看关系我们知道，FutureTask这个类实现了Runnable接口，所有里面有run方法
	public class FutureTask<V> implements RunnableFuture<V> {

		。。。

		public FutureTask(Callable<V> callable) {
	        if (callable == null)
	            throw new NullPointerException();
	        this.callable = callable;
	        this.state = NEW;       // ensure visibility of callable
	    }

	    。。。

		public void run() {
	        if (state != NEW ||
	            !U.compareAndSwapObject(this, RUNNER, null, Thread.currentThread()))
	            return;
	        try {
	            Callable<V> c = callable;
	            if (c != null && state == NEW) {
	                V result;
	                boolean ran;
	                try {
	                    result = c.call();
	                    ran = true;
	                } catch (Throwable ex) {
	                    result = null;
	                    ran = false;
	                    setException(ex);
	                }
	                if (ran)
	                    set(result);
	            }
	        } finally {
	            // runner must be non-null until state is settled to
	            // prevent concurrent calls to run()
	            runner = null;
	            // state must be re-read after nulling runner to prevent
	            // leaked interrupts
	            int s = state;
	            if (s >= INTERRUPTING)
	                handlePossibleCancellationInterrupt(s);
	        }
	    }

		。。。
	}
	run方法里调用了mWorker的call方法，最后set(result)了一下，我们来看set干啥了

	private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            if (U.compareAndSwapObject(this, WAITERS, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
    }
    set里没干啥，最后调用了这个方法，我们可以看到，这个方法回调到了done方法，就是我们刚才重写的那个done方法，最后把callable设置为了null，已经结束

5、这个是AsyncTask构造器中的一个初始化，调用到了这个的call方法，里面进行了子线程处理
	mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);

                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                //noinspection unchecked
                //调到了doInBackground方法,可以看到是在另一个进程中处理的
                Result result = doInBackground(mParams);
                Binder.flushPendingCommands();
                return postResult(result);
            }
        };

6、
	private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
                new AsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }
    这一步我们可以看出，这里只是调用了handler发送消息，然后后面接受消息，处理

7、
	private static class InternalHandler extends Handler {
        public InternalHandler() {
            super(Looper.getMainLooper());
        }

        @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
        @Override
        public void handleMessage(Message msg) {
            AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    // There is only one result
                    result.mTask.finish(result.mData[0]);
                    break;
                case MESSAGE_POST_PROGRESS:
                    result.mTask.onProgressUpdate(result.mData);
                    break;
            }
        }
    }

8、
	private void finish(Result result) {
        if (isCancelled()) {
        	//回调到onCancelled方法
            onCancelled(result);
        } else {
        	//回调到onPostExecute方法
            onPostExecute(result);
        }
        mStatus = Status.FINISHED;
    }


    我们从上面的源码流程我们可以看出，AsyncTask只是对Hanlder进行了封装，封装的很好而已










	
