package com.hide.koalio;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/** Super Mario Brothers-like very basic platformer, using a tile map built using <a href="http://www.mapeditor.org/">Tiled</a> and a
 * tileset and sprites by <a href="http://www.vickiwenderlich.com/">Vicky Wenderlich</a></p>
 *
 * Shows simple platformer collision detection as well as on-the-fly map modifications through destructible blocks!
 * [Jump sound] http://www.freesound.org/people/fins/sounds/146726/
 * [Smash sound] http://www.freesound.org/people/LittleRobotSoundFactory/sounds/270310/
 */
class SuperKoalio extends ApplicationAdapter {

	/** コアラキャラクター用のクラスを定義する */
	private static class Koala {
		static float WIDTH;					// キャラクターの幅
		static float HEIGHT;				// キャラクターの高さ
		static float MAX_VELOCITY = 10f;	// キャラクターの最大速度
		static float JUMP_VELOCITY = 40f;	// ジャンプの速度
		static float DAMPING = 0.87f;		// 移動時のキャラクターの減速加減

		// キャラクターの現在の状態を管理するための列挙型を定義する
		enum State {
			Standing,	// 立ちポーズ状態
			Walking,	// 歩行状態
			Jumping		// ジャンプ状態
		}

		final Vector2 position = new Vector2();		// キャラクターの位置
		final Vector2 velocity = new Vector2();		// キャラクターの速度
		State state = State.Walking;				// キャラクターの状態 (立ちポーズ状態で初期化)
		float stateTime = 0;						// キャラクターの画像を決定するための時間
		boolean facesRight = true;					// キャラクターの向きを管理する (右を向いている場合にtrue)
		boolean grounded = false;					// 地面に着地しているか (着地している場合にtrue)
	}

	private TiledMap map;							// ステージマップ用タイル
	private OrthogonalTiledMapRenderer renderer;	// ステージマップのレンダラー (描画処理を行う)
	private OrthographicCamera camera;				// カメラ
	private Texture koalaTexture;					// コアラのテクスチャ (描画用画像)
	private Animation stand;						// コアラの立ちポーズアニメーションを管理する
	private Animation walk;							// コアラの歩行アニメーションを管理する
	private Animation jump;							// コアラのジャンプアニメーションを管理する
	private Koala koala;							// コアラ ( = プレイヤー)
	private Sound jumpSound;
	private Sound smashSound;
	// Rectangle(矩形)のPool(プール)を作成する (ゲームの高速化/リソース有効活用のため)
	// ゲームプログラミングテクニックの一種
	private Pool<Rectangle> rectPool = new Pool<Rectangle>() {
		@Override
		protected Rectangle newObject () {
			return new Rectangle();
		}
	};
	private Array<Rectangle> tiles = new Array<Rectangle>();	// ステージマップのタイルの場所情報を一時的に保持するための配列

	private static final float GRAVITY = -2.5f;		// 重力の大きさを定義

	@Override
	public void create () {
		// コアラキャラクターのアニメーション用画像を読み込む
		// 画像を分割し、アニメーション用に設定する
		koalaTexture = new Texture("koalio.png");
		TextureRegion[] regions = TextureRegion.split(koalaTexture, 18, 26)[0];	// 読み込んだ画像("koalio.png")を分割する
		stand = new Animation(0, regions[0]);								// 立ちポーズ状態の画像を設定する
		jump = new Animation(0, regions[1]);								// ジャンプ状態の画像を設定する
		walk = new Animation(0.15f, regions[2], regions[3], regions[4]);	// 3つの画像を用いて歩行アニメーションを設定する (0.15秒間隔で画像を切り替える)
		walk.setPlayMode(Animation.PlayMode.LOOP_PINGPONG);					// 3つの画像をループ再生する

		// 衝突判定と、ゲーム中にコアラフレームをレンダリングするために、コアラの幅と高さを計算する
		// (画像16ピクセル分がゲーム中の1ユニット: 16 pixels = 1unit)
		// ちなみに、16ピクセルはゲームマップ中の1タイルの1辺の長さ
		Koala.WIDTH = 1 / 16f * regions[0].getRegionWidth();
		Koala.HEIGHT = 1 / 16f * regions[0].getRegionHeight();

		// ゲームマップを読み込む
		// コアラと同様、16 pixel = 1 unitの計算のために、読み込んだマップを1/16倍する
		map = new TmxMapLoader().load("level1.tmx");
		renderer = new OrthogonalTiledMapRenderer(map, 1 / 16f);

		// 正投影カメラを生成し、ゲームマップの30x20ユニットの大きさを撮影できるようにする
		camera = new OrthographicCamera();
		camera.setToOrtho(false, 30, 20);
		camera.update();

		// ゲームマップ中を歩き回らせるコアラ(プレイヤー)を生成して、x=20, y=20の場所にセットする
		koala = new Koala();
		koala.position.set(20, 20);

		jumpSound = Gdx.audio.newSound(Gdx.files.internal("jumping.wav"));	// ジャンプ用サウンドを読み込む
		smashSound = Gdx.audio.newSound(Gdx.files.internal("smash.wav"));	// ブロック破壊用サウンドを読み込む
	}

	@Override
	public void render () {
		// スクリーンをクリアする
		Gdx.gl.glClearColor(0.7f, 0.7f, 1.0f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		// 前回のrender呼び出しからの時間差分(delta)を取得する
		float deltaTime = Gdx.graphics.getDeltaTime();

		// コアラプレイヤーをアップデートする
		// (入力処理, 衝突判定, 位置更新)
		updateKoala(deltaTime);

		// カメラをコアラプレイヤーに追随させる (x座標のみコアラプレイヤーに合わせる)
		camera.position.x = koala.position.x;
		camera.update();

		// ゲームマップのレンダラーのビューをカメラが見ている場所にセットし、
		// マップをレンダリングする
		renderer.setView(camera);
		renderer.render();

		// コアラプレイヤーをレンダリングする
		renderKoala(deltaTime);
	}

	// コアラプレイヤーの更新メソッド
	private void updateKoala (float deltaTime) {
		// 前回のプレイヤー更新から時間が経過していない場合は、メソッド実行を抜ける
		if (deltaTime == 0) return;

		// 前回のプレイヤー更新から0.1秒以上経過している場合は、一律経過時間を0.1秒にする
		// (ゲームのカクつき防止のため)
		if (deltaTime > 0.1f)
			deltaTime = 0.1f;

		// コアラプレイヤーの状態時間(stateTime)に経過時間(deltaTime)を足し合わせる
		koala.stateTime += deltaTime;

		// 画面の右半分がタッチされ、かつコアラプレイヤーが地面に着地している場合
		if (isTouched(0.5f, 1) && koala.grounded) {
			koala.velocity.y += Koala.JUMP_VELOCITY;	// コアラプレイヤーのy方向の速度に、ジャンプ用の速度を追加する
			koala.state = Koala.State.Jumping;			// コアラプレイヤーの状態をジャンプ状態に変更する
			jumpSound.play(.5f);						// ジャンプサウンドを再生する(ボリュームを低め[0.5]で再生する)
			koala.grounded = false;						// コアラプレイヤーの着地状態をfalseにする
		}

		// 画面の左側1/4部分がタッチされた場合
		if (isTouched(0, 0.25f)) {
			koala.velocity.x = -Koala.MAX_VELOCITY;		// コアラプレイヤーのx方向の速度に、左方向への移動用の速度(マイナス値)を追加する
			if (koala.grounded) koala.state = Koala.State.Walking;	// コアラプレイヤーが着地していれば、コアラプレイヤーの状態を歩行状態にする
			koala.facesRight = false;	// コアラプレイヤーのfacingRightをfalseにする (=左を向いている状態)
		}

		// 画面左側の1/4〜半分までの部分がタッチされた場合
		if (isTouched(0.25f, 0.5f)) {
			koala.velocity.x = Koala.MAX_VELOCITY;		// コアラプレイヤーのx方向の速度に、右方向への移動用の速度(プラス値)を追加する
			if (koala.grounded) koala.state = Koala.State.Walking;	// コアラプレイヤーが着地していれば、コアラプレイヤーの状態を歩行状態にする
			koala.facesRight = true;	// コアラプレイヤーのfacingRightをtrueにする (=右を向いている状態)
		}

		// コアラプレイヤーに重力(下方向の速度: GRAVITY)を追加する
		koala.velocity.add(0, GRAVITY);

		// コアラプレイヤーのx方向の速度を-MAX_VELOCITY〜MAX_VELOCITYの間に収める
		koala.velocity.x = MathUtils.clamp(koala.velocity.x,
				-Koala.MAX_VELOCITY, Koala.MAX_VELOCITY);

		// コアラプレイヤーの速度が1よりも小さくなった場合は、一律0にセットし、
		// コアラプレイヤーの状態を立ちポーズ状態にする
		if (Math.abs(koala.velocity.x) < 1) {
			koala.velocity.x = 0;
			if (koala.grounded) koala.state = Koala.State.Standing;
		}

		// コアラプレイヤーの速度(koala.velocity)に経過時間(deltaTime)をかけ合わせ(速度x時間)、
		// 前回のプレイヤー更新からどれだけ位置が変化したかを計算する
		koala.velocity.scl(deltaTime);

		// x方向とy方向のそれぞれに対して、衝突判定と衝突時のレスポンス(反応)を処理する
		// コアラプレイヤーが右方向に動いている場合は、コアラプレイヤーの右側に位置するゲームマップのタイルをチェックする
		// 左方向に動いている場合は、左側に位置するゲームマップのタイルをチェックする
		Rectangle koalaRect = rectPool.obtain();	// プールから矩形情報を取得する (矩形情報の再利用)
		koalaRect.set(koala.position.x, koala.position.y, Koala.WIDTH, Koala.HEIGHT);	// 取得した矩形情報にコアラプレイヤーの情報をセットする
		int startX, startY, endX, endY;
		// ##### 横方向の衝突チェック #####
		if (koala.velocity.x > 0) {	// コアラプレイヤーが右方向に動いている(= x方向の速度がプラス値)の場合
			// コアラプレイヤーの右側のゲームマップを取得するためのstartXとendXをセットする
			startX = endX = (int)(koala.position.x + Koala.WIDTH + koala.velocity.x);
		} else {					// それ以外の場合(= コアラプレイヤーが左方向に動いている)
			// コアラプレイヤーの左側のゲームマップを取得するためのstartXとendXをセットする
			startX = endX = (int)(koala.position.x + koala.velocity.x);
		}
		startY = (int)(koala.position.y);				// コアラプレイヤーの下側のゲームマップを取得するためのstartYをセットする
		endY = (int)(koala.position.y + Koala.HEIGHT);	// コアラプレイヤーの上側のゲームマップを取得するためのendYをセットする
		// 上でセットしたstartX〜endX、startY〜endYの間にあるゲームマップのタイルを取得する
		getTiles(startX, startY, endX, endY, tiles);
		koalaRect.x += koala.velocity.x;	// コアラプレイヤーのx方向の位置に、変化分の位置を足し合わせる
		for (Rectangle tile : tiles) {
			if (koalaRect.overlaps(tile)) {	// ゲームマップのタイルに衝突していた場合
				koala.velocity.x = 0;	// コアラプレイヤーのx方向の速度を0にする
				break;
			}
		}
		koalaRect.x = koala.position.x;

		// コアラプレイヤーが上方向に動いている場合、コアラプレイヤーの上側に位置するゲームマップのタイルをチェックする
		// 下方向に移動している場合は、下側に位置するゲームマップのタイルをチェックする
		// ##### 縦方向の衝突チェック #####
		if (koala.velocity.y > 0) {	// コアラプレイヤーが上方向に動いている(= y方向の速度がプラス値)の場合
			// コアラプレイヤーの上側のゲームマップを取得するためのstartYとendYをセットする
			startY = endY = (int)(koala.position.y + Koala.HEIGHT + koala.velocity.y);
		} else {					// それ以外の場合(= コアラプレイヤーが下方向に動いている)
			// コアラプレイヤーの下側のゲームマップを取得するためのstartYとendYをセットする
			startY = endY = (int)(koala.position.y + koala.velocity.y);
		}
		startX = (int)(koala.position.x);				// コアラプレイヤーの左側のゲームマップを取得するためのstartXをセットする
		endX = (int)(koala.position.x + Koala.WIDTH);	// コアラプレイヤーの右側のゲームマップを取得するためのendXをセットする
		// 上でセットしたstartX〜endX、startY〜endYの間にあるゲームマップのタイルを取得する
		getTiles(startX, startY, endX, endY, tiles);
		koalaRect.y += koala.velocity.y;	// コアラプレイヤーのy方向の位置に、変化分の位置を足し合わせる
		for (Rectangle tile : tiles) {
			if (koalaRect.overlaps(tile)) {	// ゲームマップのタイルに衝突していた場合
				if (koala.velocity.y > 0) {	// 「タイルに衝突した、かつ上方向に移動している」 = 「ブロックタイルに接触 → ブロックを破壊」
					koala.position.y = tile.y - Koala.HEIGHT;	// コアラプレイヤーのy方向の位置を、衝突したブロックの真下にセットする
					TiledMapTileLayer layer = (TiledMapTileLayer)map.getLayers().get("walls");	// ゲームマップの"walls"レイヤーを取得する
					layer.setCell((int)tile.x, (int)tile.y, null);	// "walls"レイヤーのコアラプレイヤーが衝突した位置のタイルをnull(タイルなし)にする
					smashSound.play();	// ブロック破壊用のサウンドを再生する
				} else {					// 「タイルに衝突した、かつ下方向に移動している」 = 「タイルに着地した」
					koala.position.y = tile.y + tile.height;
					// コアラプレイヤーの着地状態(grounded)をtrueにセットする
					koala.grounded = true;
				}
				koala.velocity.y = 0;	// 縦方向に衝突したので、y方向の速度を0にする
				break;
			}
		}
		rectPool.free(koalaRect);

		koala.position.add(koala.velocity);	// コアラプレイヤーの位置に、今回の位置変化分を足し合わせる
		koala.velocity.scl(1 / deltaTime);	// 距離 / 時間を計算して、速度を元に戻す

		// x方向の速度を減速させる: DAMPING(1よりも小さい値)を掛け合わせることで、速度を減らしていく
		koala.velocity.x *= Koala.DAMPING;
	}

	// タッチ判定メソッド
	private boolean isTouched (float startX, float endX) {
		// x座標がstartXからendXの間の画面エリアがタッチされたかチェックする
		// startXとendXには、0(スクリーンの左端)から1(スクリーンの右端)の値を指定する
		for (int i = 0; i < 2; i++) {	// 指2本分の入力までチェックする
			float x = Gdx.input.getX(i) / (float)Gdx.graphics.getWidth();
			if (Gdx.input.isTouched(i) && (x >= startX && x <= endX)) {
				return true;
			}
		}
		return false;
	}

	// x座標がstartX〜endX、y座標がstartY〜endYに位置するゲームマップのタイルを取得する
	private void getTiles (int startX, int startY, int endX, int endY, Array<Rectangle> tiles) {
		// ゲームマップ中の"walls"レイヤーを取得する
		TiledMapTileLayer layer = (TiledMapTileLayer)map.getLayers().get("walls");
		rectPool.freeAll(tiles);
		tiles.clear();
		for (int y = startY; y <= endY; y++) {
			for (int x = startX; x <= endX; x++) {
				Cell cell = layer.getCell(x, y);
				// 現在の(x, y)の位置にcell(マップタイル)が存在すれば(nullでなければ)、
				// tiles配列にマップタイルの位置情報を追加する
				if (cell != null) {
					Rectangle rect = rectPool.obtain();
					rect.set(x, y, 1, 1);
					tiles.add(rect);
				}
			}
		}
	}

	// コアラプレイヤーをレンダリングするメソッド
	private void renderKoala (float deltaTime) {
		// 現在のコアラの状態(koala.stateTime)を基に、アニメーションフレームを取得する
		TextureRegion frame = null;
		switch (koala.state) {
			case Standing:	// 立ちポーズ状態の場合
				frame = stand.getKeyFrame(koala.stateTime);
				break;
			case Walking:	// 歩行状態の場合
				frame = walk.getKeyFrame(koala.stateTime);
				break;
			case Jumping:	// ジャンプ状態の場合
				frame = jump.getKeyFrame(koala.stateTime);
				break;
		}

		// x方向の現在の速度を基に、コアラプレイヤーを描画する
		// コアラプレイヤーの向き情報(koala.facesRight)を基に、画像の描画方向(左右)を切り替える
		Batch batch = renderer.getBatch();
		batch.begin();
		if (koala.facesRight) {
			batch.draw(frame, koala.position.x, koala.position.y, Koala.WIDTH, Koala.HEIGHT);
		} else {
			batch.draw(frame, koala.position.x + Koala.WIDTH, koala.position.y, -Koala.WIDTH, Koala.HEIGHT);
		}
		batch.end();
	}

	@Override
	public void dispose () {
		jumpSound.dispose();	// ジャンプ用サウンドを破棄する
		smashSound.dispose();	// ブロック破壊用サウンドを破棄する
	}
}
