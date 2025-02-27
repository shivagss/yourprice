package com.aviary.android.feather.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.SDKUtils;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.headless.AviaryExecutionException;
import com.aviary.android.feather.headless.utils.MegaPixels;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.services.BadgeService;
import com.aviary.android.feather.library.services.BaseContextService;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.DragControllerService;
import com.aviary.android.feather.library.services.HiResBackgroundService;
import com.aviary.android.feather.library.services.IAPService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.services.ServiceLoader;
import com.aviary.android.feather.library.services.ThreadPoolService;
import com.aviary.android.feather.library.services.drag.DragLayer;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.ImageInfo;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.sdk.panels.AbstractPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanel.ContentPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnApplyResultListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnContentReadyListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnErrorListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnPreviewListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnProgressListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OptionPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanelLoaderService;
import com.aviary.android.feather.sdk.widget.AviaryBottomBarViewFlipper;
import com.aviary.android.feather.sdk.widget.AviaryBottomBarViewFlipper.OnViewChangingStatusListener;

import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;

/**
 * Main controller.<br />
 * It manages all the tools, notifies about new plugins installed, etc...
 * 
 * @author alessandro
 */
public final class AviaryMainController implements IAviaryController, OnPreviewListener, OnApplyResultListener, OnErrorListener, OnContentReadyListener,
		OnProgressListener, HiResBackgroundService.OnHiresListener {

	/**
	 * The Interface FeatherContext.<br />
	 * The activity caller must implement this interface
	 */
	public interface FeatherContext {

		/**
		 * Gets the Activity main image view.
		 * 
		 * @return the main image
		 */
		ImageViewTouchBase getMainImage();

		/**
		 * Gets the Activity bottom bar view.
		 * 
		 * @return the bottom bar
		 */
		AviaryBottomBarViewFlipper getBottomBar();

		/**
		 * Gets the Activity options panel container view.
		 * 
		 * @return the options panel container
		 */
		ViewGroup getOptionsPanelContainer();

		/**
		 * Gets the Activity drawing image container view.
		 * 
		 * @return the drawing image container
		 */
		ViewGroup getDrawingImageContainer();

		/**
		 * There's a special container drawn on top of all views, which can be used to add
		 * custom dialogs/popups.
		 * This is invisible by default and must be activated in order to be used
		 * 
		 * @return
		 */
		ViewGroup activatePopupContainer();

		/**
		 * When the there's no need to use the popup container anymore, you must
		 * deactivate it
		 */
		void deactivatePopupContainer();

		/**
		 * Show tool progress.
		 */
		void showToolProgress();

		/**
		 * Hide tool progress.
		 */
		void hideToolProgress();

		/**
		 * Show a modal progress
		 */
		void showModalProgress();

		/**
		 * Hide the modal progress
		 */
		void hideModalProgress();

		AviaryTracker getTracker();
	}

	public interface OnBitmapChangeListener {

		public void onBitmapChange( Bitmap bitmap, boolean update, Matrix matrix );

		public void onPreviewChange( Bitmap bitmap, boolean reset );

		public void onInvalidateBitmap();
	}

	/**
	 * The listener interface for receiving onTool events. The class that is interested in
	 * processing a onTool event implements this
	 * interface, and the object created with that class is registered with a component
	 * using the component's <code>addOnToolListener<code> method. When
	 * the onTool event occurs, that object's appropriate
	 * method is invoked.
	 * 
	 */
	public static interface OnToolListener {

		/**
		 * On tool completed.
		 */
		void onToolCompleted();
	}

	/**
	 * All the possible states the filtermanager can use during the feather lifecycle.
	 * 
	 * @author alessandro
	 */
	static enum STATE {
		CLOSED_CANCEL, CLOSED_CONFIRMED, CLOSING, DISABLED, OPENED, OPENING,
	}

	public static final int STATE_OPENING = 0;
	public static final int STATE_OPENED = 1;
	public static final int STATE_CLOSING = 2;
	public static final int STATE_CLOSED = 3;
	public static final int STATE_DISABLED = 4;
	public static final int STATE_CONTENT_READY = 5;
	public static final int STATE_READY = 6;

	public static final int TOOLBAR_TITLE = 100;
	public static final int TOOLBAR_TITLE_INT = 101;
	public static final int TOOLBAR_APPLY_VISIBILITY = 102;

	/** The current bitmap. */
	private Bitmap mBitmap;

	/** The original bitmap, used for pre-post and restore */
	private Bitmap mOriginalBitmap;

	/** The base context. This is the main activity */
	private FeatherContext mContext;

	/** The current active effect. */
	private AbstractPanel mCurrentEffect;

	/** The current active effect entry. */
	private ToolEntry mCurrentEntry;

	/** The list of enabled tools, if it is null, we assume all tools are enabled */
	private List<String> mToolList;

	/** The current panel state. */
	private STATE mCurrentState;

	/** The main tool listener. */
	private OnToolListener mToolListener;

	/** bitmap change listener */
	private OnBitmapChangeListener mBitmapChangeListener;

	private final Handler mHandler;
	private final ServiceLoader<BaseContextService> mServiceLoader;
	private AbstractPanelLoaderService mPanelCreatorService;
	private Logger logger;

	/** The changed state. If the original image has been modified. */
	private boolean mChanged;

	private Configuration mConfiguration;

	private String mSessionId;

	private List<HiResBackgroundService.OnHiresListener> mHiresListeners;

	private DragLayer mDragLayer;

	/** true when the app has been updated from the google play store */
	private Boolean mAppIsUpdated;

	private int mToolCompleteCount;

	/**
	 * Instantiates a new filter manager.
	 * 
	 * @param context
	 *            the context
	 * @param handler
	 *            the handler
	 */
	public AviaryMainController ( final FeatherContext context, final Handler handler ) {
		logger = LoggerFactory.getLogger( "AviaryMainController", LoggerType.ConsoleLoggerType );
		mContext = context;
		mHandler = handler;
		mToolCompleteCount = 0;
		mHiresListeners = new ArrayList<HiResBackgroundService.OnHiresListener>(0);

		mServiceLoader = new ServiceLoader<BaseContextService>( this );
		mConfiguration = new Configuration( ( (Context) context ).getResources().getConfiguration() );

		initServices( context );
		setCurrentState( STATE.DISABLED );
		mChanged = false;
	}

	public void addOnHiresListener( HiResBackgroundService.OnHiresListener listener ) {
		mHiresListeners.add( listener );
	}

	public boolean removeOnHiresListener( HiResBackgroundService.OnHiresListener listener ) {
		return mHiresListeners.remove(listener);
	}

	@Override
	public AviaryTracker getTracker () {
		return mContext.getTracker();
	}

	/**
	 * Returns the total count of tools that
	 * have been applied during the session
	 * @return
	 */
	public final int getToolCompleteCount() {
		return mToolCompleteCount;
	}

	protected void setToolList(List<String> toolList) {
		mToolList = toolList;
	}

	protected List<String> getToolList() {
		return mToolList;
	}

	/**
	 * Returns true if the app has been updated from an older version
	 * and this is the first time it's running
	 * 
	 * @return
	 */
	public boolean getAppIsUpdated() {
		if ( null == mAppIsUpdated ) {
			PreferenceService service = getService( PreferenceService.class );
			if ( null != service ) {

				int versionCode;

				PackageInfo info = PackageManagerUtils.getPackageInfo( getBaseContext() );

				if ( null != info ) {
					versionCode = info.versionCode;
				} else {
					versionCode = SDKUtils.SDK_VERSION_CODE;
				}

				int registeredVersion = service.getInt( "aviary-package-version", 0 );

				logger.info( "registered version: " + registeredVersion + ", my version: " + versionCode );

				if ( registeredVersion != versionCode ) {
					mAppIsUpdated = true;
					service.putInt( "aviary-package-version", versionCode );
				} else {
					mAppIsUpdated = false;
				}
			} else {
				logger.error( "can't open preferenceService" );
				mAppIsUpdated = false;
			}
		}

		return mAppIsUpdated.booleanValue();
	}

	public void setDragLayer( DragLayer view ) {
		mDragLayer = view;
	}

	private synchronized void initServices( final FeatherContext context ) {
		logger.info( "initServices" );

		mServiceLoader.register(LocalDataService.class);
		mServiceLoader.register(ThreadPoolService.class);
		mServiceLoader.register(ConfigService.class);
		mServiceLoader.register(IAPService.class);
		mServiceLoader.register(BadgeService.class);
		mServiceLoader.register(HiResBackgroundService.class);
		mServiceLoader.register(DragControllerService.class);
		mServiceLoader.register(PreferenceService.class);
		mServiceLoader.register(AbstractPanelLoaderService.class);
	}

	private void initHiResService( final Bitmap bitmap, final ImageInfo imageInfo ) {
		logger.info( "initHiResService" );
		LocalDataService dataService = getService( LocalDataService.class );
		HiResBackgroundService service = getService( HiResBackgroundService.class );

		boolean hires_enabled = true;

		if( null != bitmap && null != imageInfo ) {

			if( null != imageInfo.getOriginalSize() && imageInfo.getOriginalSize().length == 2) {
				logger.log( "original size: %dx%d", imageInfo.getOriginalSize()[0], imageInfo.getOriginalSize()[1] );
				logger.log( "bitmap size: %dx%d", bitmap.getWidth(), bitmap.getHeight() );
			}
			// hires_enabled = imageInfo.getOriginalSize()[0] > bitmap.getWidth() || imageInfo.getOriginalSize()[1] > bitmap.getHeight();
			// logger.warn( "hires_enabled: " + hires_enabled );
		}

		if( !service.isRunning() ) {
			service.setOnHiresListener( this );
			service.start();
			service.load(dataService.getSourceImageUri(), dataService.getRequestedMegaPixels(), imageInfo);
		}
	}

	public void activateTool( final ToolEntry tag ) {
		activateTool(tag, null);
	}

	/**
	 * This is the entry point of every aviary tool
	 * 
	 * @param tag
	 *            indicates which tool to activate
	 * @param options
	 *            an optional Bundle to be passed to the created {@link AbstractPanel}
	 */
	public void activateTool( final ToolEntry tag, Bundle options ) {
		if ( !getEnabled() || !isClosed() || mBitmap == null ) return;

		// check if the tool is enabled. if mToolList is null, just assume enabled:
		if ( mToolList != null ) {
			if( mToolList.indexOf( tag.name.name() ) < 0 ) {
				// throw new IllegalStateException( "Selected entry is not valid" );
				return;
			}
		}

		if ( mCurrentEffect != null ) throw new IllegalStateException( "There is already an active effect. Cannot activate new" );
		if ( mPanelCreatorService == null ) mPanelCreatorService = getService( AbstractPanelLoaderService.class );

		final AbstractPanel effect = mPanelCreatorService.createNew( tag );

		if ( effect != null ) {
			mCurrentEffect = effect;
			mCurrentEntry = tag;

			// mark tool as read
			BadgeService badge = getService( BadgeService.class );
			badge.markAsRead( tag.name );

			setCurrentState( STATE.OPENING );
			prepareToolPanel( effect, tag, options );

			getTracker().tagEvent( mCurrentEntry.name.name().toLowerCase( Locale.US ) + ": opened" );

			mContext.getBottomBar().setOnViewChangingStatusListener( new OnViewChangingStatusListener() {

				@Override
				public void OnOpenStart() {
					mCurrentEffect.onOpening();
				}

				@Override
				public void OnOpenEnd() {
					setCurrentState( STATE.OPENED );
					mContext.getBottomBar().setOnViewChangingStatusListener( null );
				}

				@Override
				public void OnCloseStart() {}

				@Override
				public void OnCloseEnd() {}
			} );

			mContext.getBottomBar().open();
		}
	}

	public void dispose() {
		if ( mCurrentEffect != null ) {
			logger.log( "Deactivate and destroy current panel" );
			mCurrentEffect.onDeactivate();
			mCurrentEffect.onDestroy();
			mCurrentEffect = null;
		}

		HiResBackgroundService hiresService = getService( HiResBackgroundService.class );
		if( null != hiresService ) {
			hiresService.setOnHiresListener( null );
		}
		
		mServiceLoader.dispose();
		mContext = null;
		mToolListener = null;
		mBitmapChangeListener = null;

		System.gc();
	}

	@Override
	public Context getBaseContext() {
		return (Activity) mContext;
	}

	@Override
	public Activity getBaseActivity() {
		return (Activity) mContext;
	}

	/**
	 * Return the current bitmap.
	 * 
	 * @return the bitmap
	 */
	public Bitmap getBitmap() {
		return mBitmap;
	}

	public Bitmap getOriginalBitmap() {
		return mOriginalBitmap;
	}

	/**
	 * Return true if the main image has been modified by any of the feather tools.
	 * 
	 * @return the bitmap is changed
	 */
	public boolean getBitmapIsChanged() {
		return mChanged;
	}

	/**
	 * Returns true if the main image has been modified or there's an
	 * active panel which is modifying it
	 * 
	 * @return
	 */
	public boolean getBitmapIsChangedOrChanging() {
		if ( mChanged ) return true;
		if ( null != mCurrentEffect && mCurrentEffect.isActive() ) {
			if ( mCurrentEffect.getIsChanged() ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if there's an active tool which has
	 * an active render task
	 * 
	 * @return
	 */
	public boolean getPanelIsRendering() {
		if ( null != mCurrentEffect && mCurrentEffect.isActive() ) {
			return mCurrentEffect.isRendering();
		}
		return false;
	}

	/**
	 * Returns the active tool, null if there is not active tool.
	 * 
	 * @return
	 */
	@Override
	public ToolEntry getActiveTool() {
		return mCurrentEntry;
	}

	/**
	 * Return the current panel associated with the active tool. Null if there's no active
	 * tool
	 * 
	 * @return the current panel
	 */
	public AbstractPanel getActiveToolPanel() {
		return mCurrentEffect;
	}

	/**
	 * Return the current image transformation matrix. this is useful for those tools
	 * which implement ContentPanel and want to
	 * display the preview bitmap with the same zoom level of the main image
	 * 
	 * @return the current image view matrix
	 * @see ContentPanel
	 */
	@Override
	public Matrix getCurrentImageViewMatrix() {
		return mContext.getMainImage().getDisplayMatrix();
	}

	/**
	 * Return true if enabled.
	 * 
	 * @return the enabled
	 */
	public boolean getEnabled() {
		return mCurrentState != STATE.DISABLED;
	}

	/**
	 * Return the service, if previously registered using ServiceLoader.
	 * 
	 * @param <T>
	 *            the generic type
	 * @param cls
	 *            the cls
	 * @return the service
	 */
	@SuppressWarnings ( "unchecked" )
	@Override
	public <T> T getService( Class<T> cls ) {
		try {
			return (T) mServiceLoader.getService( (Class<BaseContextService>) cls );
		} catch ( IllegalAccessException e ) {
			e.printStackTrace();
			return null;
		}
	}

	public void registerService( Class<? extends BaseContextService> cls ) {
		mServiceLoader.register( cls );
	}

	/**
	 * Remove any running instance of the {@link BaseContextService} and delete it from
	 * the registered services
	 * 
	 * @param cls
	 */
	public void removeService( Class<? extends BaseContextService> cls ) {
		mServiceLoader.remove( cls );
	}

	/**
	 * Return true if there's no active tool.
	 * 
	 * @return true, if is closed
	 */
	public boolean isClosed() {
		return ( mCurrentState == STATE.CLOSED_CANCEL ) || ( mCurrentState == STATE.CLOSED_CONFIRMED );
	}

	/**
	 * return true if there's one active tool.
	 * 
	 * @return true, if is opened
	 */
	public boolean isOpened() {
		return mCurrentState == STATE.OPENED;
	}

	/**
	 * On activate.
	 *
	 * @param bitmap the bitmap
	 * @param imageInfo the info about the loaded image
	 */
	public void onActivate( final Bitmap bitmap, ImageInfo imageInfo ) {
		if ( mCurrentState != STATE.DISABLED ) throw new IllegalStateException( "Cannot activate. Already active!" );

		if ( ( mBitmap != null ) && !mBitmap.isRecycled() ) {
			mBitmap = null;
		}

		mBitmap = bitmap;

		// let's activate the original image restore options only
		// if the device has at least 256mb per app and with android 4.x
		if ( SystemUtils.getApplicationTotalMemory() >= 256 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ) {
			mOriginalBitmap = BitmapUtils.copy( mBitmap, mBitmap.getConfig() );
		}

		LocalDataService dataService = getService( LocalDataService.class );
		dataService.setImageInfo( imageInfo );

		mChanged = false;
		setCurrentState( STATE.CLOSED_CONFIRMED );
		initHiResService( bitmap, imageInfo );
	}

	/**
	 * @param requestCode
	 * @param resultCode
	 * @param data
	 * @return true if the action has been handled
	 */
	public boolean onActivityResult( int requestCode, int resultCode, Intent data ) {
		IAPService service = getService( IAPService.class );
		if ( null != service && requestCode == IAPService.PURCHASE_FLOW_REQUEST_CODE ) {
			try {
				return service.handleActivityResult( requestCode, resultCode, data );
			} catch( IllegalStateException e ) {
				logger.error( "handled exception" );
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * Current activity is asking to apply the current tool.
	 */
	public void onApply() {
		logger.info( "FilterManager::onapply" );
		if ( !getEnabled() || !isOpened() ) return;

		if ( mCurrentEffect == null ) throw new IllegalStateException( "there is no current effect active in the context" );

		if ( !mCurrentEffect.isEnabled() ) return;

		if ( mCurrentEffect.getIsChanged() ) {
			mCurrentEffect.onSave();
			mChanged = true;
		} else {
			onCancel();
		}
	}

	/**
	 * Parent activity just received a onBackPressed event. If there's one active tool, it
	 * will be asked to manage the onBackPressed
	 * event. If the active tool onBackPressed method return a false then try to close it.
	 * 
	 * @return true, if successful
	 */
	public boolean onBackPressed() {
		if ( isClosed() ) return false;
		if ( mCurrentState != STATE.DISABLED ) {
			if ( isOpened() ) {
				if ( !mCurrentEffect.onBackPressed() ) onCancel();
			}
			return true;
		}
		return false;
	}

	/**
	 * Main activity asked to cancel the current operation.
	 */
	public void onCancel() {
		if ( !getEnabled() || !isOpened() ) return;
		if ( mCurrentEffect == null ) throw new IllegalStateException( "there is no current effect active in the context" );
		if ( !mCurrentEffect.onCancel() ) {
			cancel();
		}
	}

	@Override
	public void cancel() {

		logger.info("FilterManager::cancel");

		if ( !getEnabled() || !isOpened() ) return;
		if ( mCurrentEffect == null ) throw new IllegalStateException( "there is no current effect active in the context" );

		mContext.getTracker().tagEvent(mCurrentEntry.name.name().toLowerCase(Locale.US) + ": cancelled");

		// send the cancel event to the effect
		mCurrentEffect.onCancelled();

		// check changed image
		if ( mCurrentEffect.getIsChanged() ) {
			// panel is changed, restore the original bitmap
			setNextBitmap( mBitmap, true );
		} else {
			// panel is not changed
			setNextBitmap( mBitmap, true );
		}
		onClose( false );
	}

	/**
	 * On close.
	 * 
	 * @param isConfirmed
	 *            the is confirmed
	 */
	private void onClose( final boolean isConfirmed ) {

		logger.info( "onClose" );

		setCurrentState( STATE.CLOSING );

		mContext.getBottomBar().setOnViewChangingStatusListener( new OnViewChangingStatusListener() {

			@Override
			public void OnOpenStart() {}

			@Override
			public void OnOpenEnd() {}

			@Override
			public void OnCloseStart() {
				mCurrentEffect.onClosing();
			}

			@Override
			public void OnCloseEnd() {
				setCurrentState( isConfirmed ? STATE.CLOSED_CONFIRMED : STATE.CLOSED_CANCEL );
				mContext.getBottomBar().setOnViewChangingStatusListener( null );
			}
		} );

		mContext.getBottomBar().close();
	}

	@Override
	public void onComplete(final Bitmap result, EditToolResultVO editResultVO) {
		mToolCompleteCount += 1;

		if ( result != null ) {
			setNextBitmap( result, true );
		} else {
			logger.error( "Error: returned bitmap is null!" );
			setNextBitmap( mBitmap, true );
		}

		onClose( true );

		if (null != editResultVO) {

			// validate the result object ( this will throw an AssertionError )
			if(!editResultVO.valid()){
				logger.error("editResult is not valid!");
			}

			// add the recipe to the data service
			LocalDataService dataService = getService(LocalDataService.class);
			if (null != dataService) {
				dataService.addRecipe(editResultVO.getEditTool());
			}

			if( null != editResultVO.getActionList() && editResultVO.getActionList().size() > 0 ) {
				// send the actions to the hires service
				HiResBackgroundService service = getService(HiResBackgroundService.class);
				if (null != service && service.isRunning()) {
					service.apply(editResultVO.getActionList());
				}
			} else {
				logger.error("actionlist is missing!");
			}
		} else {
			logger.error("Something was wrong, edit result is null!");
		}
	}

	/**
	 * Sets the next bitmap.
	 * 
	 * @param bitmap
	 *            the new next bitmap
	 */
	void setNextBitmap( Bitmap bitmap ) {
		setNextBitmap( bitmap, true );
	}

	/**
	 * Sets the next bitmap.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @param update
	 *            the update
	 */
	void setNextBitmap( Bitmap bitmap, boolean update ) {
		setNextBitmap(bitmap, update, null);
	}

	/**
	 * Sets the next bitmap.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @param update
	 *            the update
	 * @param matrix
	 *            the matrix
	 */
	void setNextBitmap( Bitmap bitmap, boolean update, Matrix matrix ) {
		logger.log( "setNextBitmap", bitmap, update, matrix );

		if ( null != mBitmapChangeListener ) mBitmapChangeListener.onBitmapChange( bitmap, update, matrix );

		if ( !mBitmap.equals( bitmap ) && !mBitmap.isRecycled() ) {
			logger.warn( "[recycle] original Bitmap: " + mBitmap );
			mBitmap.recycle();
			mBitmap = null;
		}
		mBitmap = bitmap;
	}

	@Override
	public void onError( CharSequence message, int yesLabel, OnClickListener yesListener ) {
		new AlertDialog.Builder( (Activity) mContext ).setTitle( R.string.feather_generic_error_title ).setMessage( message )
				.setPositiveButton(yesLabel, yesListener).setIcon(android.R.drawable.ic_dialog_alert).show();
	}

	@Override
	public void onError( CharSequence message, int yesLabel, OnClickListener yesListener, int noLabel, OnClickListener noListener ) {
		new AlertDialog.Builder( (Activity) mContext ).setTitle( R.string.feather_generic_error_title ).setMessage( message )
				.setPositiveButton(yesLabel, yesListener).setNegativeButton(noLabel, noListener).setIcon( android.R.drawable.ic_dialog_alert ).show();
	}

	@Override
	public void onMessage( CharSequence title, CharSequence message, int yesLabel, OnClickListener yesListener ) {
		new AlertDialog.Builder( (Activity) mContext ).setTitle( title ).setMessage( message ).setIcon( android.R.drawable.ic_dialog_info )
				.setPositiveButton( yesLabel, yesListener ).show();
	}

	@Override
	public void onMessage( CharSequence title, CharSequence message, int yesLabel, OnClickListener yesListener, int noLabel, OnClickListener noListener ) {
		new AlertDialog.Builder( (Activity) mContext ).setTitle( title ).setMessage( message ).setPositiveButton( yesLabel, yesListener )
				.setNegativeButton( noLabel, noListener ).setIcon( android.R.drawable.ic_dialog_info ).show();
	}

	@Override
	public void onPreviewChange( final Bitmap result, boolean reset ) {
		if ( !getEnabled() || !isOpened() ) return;
		if ( null != mBitmapChangeListener ) mBitmapChangeListener.onPreviewChange( result, reset );
	}

	@Override
	public void onPreviewUpdated() {
		if ( !getEnabled() || !isOpened() ) return;
		if ( null != mBitmapChangeListener ) mBitmapChangeListener.onInvalidateBitmap();
	}

	@Override
	public void onReady( final AbstractPanel panel ) {
		mHandler.sendEmptyMessage( STATE_CONTENT_READY );
		mHandler.sendEmptyMessage( STATE_READY );
	}

	public void onRestoreOriginal() {
		logger.info( "onRestoreOriginal" );

		// if not enabled, return
		if ( !getEnabled() ) return;

		// if the original bitmap is null, return
		if ( null == mOriginalBitmap ) return;

		// copy the original bitmap into a temporary bitmap;
		Bitmap next = BitmapUtils.copy( mOriginalBitmap, mOriginalBitmap.getConfig() );

		// replace the current bitmap with the temp one
		setNextBitmap( next, true, null );
		mChanged = false;


		// restore the original image
		LocalDataService dataService = getService( LocalDataService.class );
		HiResBackgroundService hiresService = getService( HiResBackgroundService.class );

		if( null != dataService ) {
			dataService.clearRecipe();
		}

		if( null != hiresService && null != dataService && hiresService.isRunning() ) {
			// note that if this is a 'reload' operation we should remove all
			// the references to the old loaded image
			hiresService.load(dataService.getSourceImageUri(), MegaPixels.Mp15.ordinal(), dataService.getImageInfo());
		}

		// now send the "replaced" message to the current panel, if one opened
		if ( null != mCurrentEffect ) {
			mCurrentEffect.onBitmapReplaced( mBitmap );
		}
	}

	/**
	 * On save.
	 */
	public void onSave() {
		if ( !getEnabled() || !isClosed() ) return;
	}

	/**
	 * Prepare tool panel.
	 * 
	 * @param effect
	 * @param entry
	 */
	private void prepareToolPanel( final AbstractPanel effect, final ToolEntry entry, Bundle options ) {
		View option_child = null;
		View drawing_child = null;

		if ( effect instanceof OptionPanel ) {
			option_child = ( (OptionPanel) effect ).getOptionView( LayoutInflater.from( (Context) mContext ), mContext.getOptionsPanelContainer() );
			mContext.getOptionsPanelContainer().addView( option_child );
		}

		if ( effect instanceof ContentPanel ) {
			drawing_child = ( (ContentPanel) effect ).getContentView( LayoutInflater.from( (Context) mContext ) );
			drawing_child.setLayoutParams( new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
			mContext.getDrawingImageContainer().addView( drawing_child );
		}

		effect.onCreate( mBitmap, options );
	}

	/**
	 * Run a Runnable on the main UI thread.
	 * 
	 * @param action
	 *            the action
	 */
	@Override
	public void runOnUiThread( final Runnable action ) {
		if ( mContext != null ) ( (Activity) mContext ).runOnUiThread( action );
	}

	/**
	 * Sets the current state.
	 * 
	 * @param newState
	 *            the new current state
	 */
	private void setCurrentState( final STATE newState ) {
		if ( newState != mCurrentState ) {
			final STATE previousState = mCurrentState;
			mCurrentState = newState;

			switch ( newState ) {
				case OPENING:
					mCurrentEffect.setOnPreviewListener( this );
					mCurrentEffect.setOnApplyResultListener( this );
					mCurrentEffect.setOnErrorListener( this );
					mCurrentEffect.setOnProgressListener( this );

					if ( mCurrentEffect instanceof ContentPanel ) ( (ContentPanel) mCurrentEffect ).setOnReadyListener( this );

					mHandler.sendEmptyMessage( AviaryMainController.STATE_OPENING );
					break;

				case OPENED:
					mCurrentEffect.onActivate();
					mHandler.sendEmptyMessage( AviaryMainController.STATE_OPENED );

					if ( !( mCurrentEffect instanceof ContentPanel ) ) {
						mHandler.sendEmptyMessage( STATE_READY );
					}

					break;

				case CLOSING:
					mHandler.sendEmptyMessage( AviaryMainController.STATE_CLOSING );

					mCurrentEffect.onDeactivate();
					if ( mCurrentEffect instanceof ContentPanel ) {
						( (ContentPanel) mCurrentEffect ).setOnReadyListener( null );
					}

					// TODO: use a delay?
					mHandler.post( new Runnable() {

						@Override
						public void run() {
							mContext.getDrawingImageContainer().removeAllViews();
							mContext.deactivatePopupContainer();
						}
					} );
					break;

				case CLOSED_CANCEL:
				case CLOSED_CONFIRMED:

					mContext.getOptionsPanelContainer().removeAllViews();

					if ( previousState != STATE.DISABLED ) {
						mCurrentEffect.onDestroy();
						mCurrentEffect.setOnPreviewListener( null );
						mCurrentEffect.setOnApplyResultListener( null );
						mCurrentEffect.setOnErrorListener( null );
						mCurrentEffect.setOnProgressListener( null );
						mCurrentEffect = null;
						mCurrentEntry = null;
					}

					mHandler.sendEmptyMessage( AviaryMainController.STATE_CLOSED );

					if ( ( newState == STATE.CLOSED_CONFIRMED ) && ( previousState != STATE.DISABLED ) ) {
						if ( mToolListener != null ) {
							mToolListener.onToolCompleted();
						}
					}
					System.gc();
					break;

				case DISABLED:
					mHandler.sendEmptyMessage( AviaryMainController.STATE_DISABLED );
					break;
			}
		}
	}

	/**
	 * Sets the enabled.
	 * 
	 * @param value
	 *            the new enabled
	 */
	public void setEnabled( final boolean value ) {
		if ( !value ) {
			if ( isClosed() ) {
				setCurrentState( STATE.DISABLED );
			} else {
				logger.warn( "FilterManager must be closed to change state" );
			}
		}
	}

	/**
	 * Sets the on tool listener.
	 * 
	 * @param listener
	 *            the new on tool listener
	 */
	public void setOnToolListener( final OnToolListener listener ) {
		mToolListener = listener;
	}

	public void setOnBitmapChangeListener( final OnBitmapChangeListener listener ) {
		mBitmapChangeListener = listener;
	}

	/**
	 * Main Activity configuration changed We want to dispatch the configuration event
	 * also to the opened panel.
	 * 
	 * @param newConfig
	 *            the new config
	 * @return true if the event has been handled
	 */
	public boolean onConfigurationChanged( Configuration newConfig ) {

		boolean result = false;
		logger.info( "onConfigurationChanged: " + newConfig.orientation + ", " + mConfiguration.orientation );

		if ( mCurrentEffect != null ) {
			if ( mCurrentEffect.isCreated() ) {
				logger.info( "onConfigurationChanged, sending event to ", mCurrentEffect );
				mCurrentEffect.onConfigurationChanged( newConfig, mConfiguration );
				result = true;
			}
		}

		mConfiguration = new Configuration( newConfig );
		return result;
	}

	@Override
	public void onProgressStart() {
		mContext.showToolProgress();
	}

	@Override
	public void onProgressEnd() {
		mContext.hideToolProgress();
	}

	@Override
	public void onProgressModalStart() {
		mContext.showModalProgress();
	}

	@Override
	public void onProgressModalEnd() {
		mContext.hideModalProgress();
	}

	@Override
	public void setToolbarTitle( int resId ) {
		final Message message = mHandler.obtainMessage( TOOLBAR_TITLE_INT, resId, 0 );
		mHandler.sendMessage( message );
	}

	@Override
	public void setToolbarTitle( CharSequence value ) {
		final Message message = mHandler.obtainMessage( TOOLBAR_TITLE, value );
		mHandler.sendMessage( message );
	}

	@Override
	public void restoreToolbarTitle() {

		if ( null != mCurrentEntry ) {
			final Message message = mHandler.obtainMessage( TOOLBAR_TITLE_INT, mCurrentEntry.labelResourceId, 0 );
			mHandler.sendMessage( message );
		}
	}

	@Override
	public void setPanelApplyStatusEnabled( boolean enabled ) {
		final Message message = mHandler.obtainMessage( TOOLBAR_APPLY_VISIBILITY, enabled ? 1 : 0, 0 );
		mHandler.sendMessage( message );
	}

	@Override
	public DragLayer getDragLayer() {
		return mDragLayer;
	}

	// ----------------------
	// HIRES listener methods
	// ----------------------

	@Override
	public void onHiresError( final AviaryExecutionException ex ) {
		logger.info( "onHiresError: " + ex );

		for( HiResBackgroundService.OnHiresListener listener : mHiresListeners ) {
			listener.onHiresError( ex );
		}
	}

	@Override
	public void onHiresProgress( final int index, final int total ) {
		logger.info( "onHiresProgress: %d of %d", index, total );

		for( HiResBackgroundService.OnHiresListener listener : mHiresListeners ) {
			listener.onHiresProgress( index, total );
		}
	}

	@Override
	public void onHiresComplete() {
		logger.info( "onHiresComplete" );

		for( HiResBackgroundService.OnHiresListener listener : mHiresListeners ) {
			listener.onHiresComplete();
		}
	}
}
