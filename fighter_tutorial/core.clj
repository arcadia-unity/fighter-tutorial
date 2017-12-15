(ns fighter-tutorial.core
  (:use arcadia.core arcadia.linear)
  (:import [UnityEngine Collider2D Physics
            GameObject Input Rigidbody2D
            Vector2 Mathf Resources Transform
            Collision2D Physics2D]
           ArcadiaState))

;; The following is completed code for the tutorial.
;; You can follow along by either uncommenting chunks of code as the
;; tutorial gets to them, or writing your own in step with the
;; tutorial. To see the full game, uncomment everything below and
;; run (setup) from the repl.

;; (def max-velocity 1)
;; (def acceleration 0.001)

;; (defn bearing-vector [angle]
;;   (let [angle (* Mathf/Deg2Rad angle)]
;;     (v2 (Mathf/Cos angle) (Mathf/Sin angle))))

;; (defn abs-angle [v]
;;   (* Mathf/Rad2Deg
;;      (Mathf/Atan2 (.y v) (.x v))))

;; (defn controller-vector []
;;   (v2 (Input/GetAxis "Horizontal")
;;     (Input/GetAxis "Vertical")))

;; (defn wasd-key []
;;   (or (Input/GetKey "w")
;;       (Input/GetKey "a")
;;       (Input/GetKey "s")
;;       (Input/GetKey "d")))

;; (defn do-ignore-collisions [^Collider2D col1, ^Collider2D col2]
;;   (Physics2D/IgnoreCollision col1 col2 true)
;;   (Physics2D/IgnoreCollision col2 col1 true))

;; (defn move-forward [^Rigidbody2D rb, distance]
;;   (.MovePosition rb
;;     (v2+ (.position rb)
;;       (v2* (bearing-vector (.rotation rb))
;;         distance))))

;; ;; ============================================================
;; ;; health

;; ;; health, remove from scene when zero
;; ;; expects to be keyed at ::health
;; (defrole health-role
;;   :state {:health 1}
;;   (update [obj k]
;;     (let [{:keys [health]} (state obj k)]
;;       (when (<= health 0)
;;         (retire obj)))))

;; (defn damage [obj amt]
;;   (update-state obj ::health update :health - amt))

;; ;; ============================================================
;; ;; bullet

;; (def bullet-layer (UnityEngine.LayerMask/NameToLayer "bullets"))

;; ;; ------------------------------------------------------------
;; ;; bullet collision

;; (defrole bullet-collision
;;   (on-trigger-enter2d [bullet, ^Collider2D collider, k]
;;     ;; this part is stupid
;;     (when (cmpt (.. collider gameObject) ArcadiaState)
;;       (let [obj2 (.. collider gameObject)]
;;         (when (state obj2 ::health) ;; there should be a fast has-state? predicate
;;           (damage obj2 1)
;;           (retire bullet))))))

;; ;; ------------------------------------------------------------
;; ;; bullet lifespans

;; (defrole lifespan-role 
;;   :state {:start System.DateTime/Now
;;           :lifespan 0}
;;   (update [obj k]
;;     (let [{:keys [start lifespan]} (state obj k)]
;;       (when (< lifespan (.TotalMilliseconds (.Subtract System.DateTime/Now start)))
;;         (retire obj)))))

;; ;; ------------------------------------------------------------
;; ;; bullet movement

;; (defrole bullet-movement-role
;;   (fixed-update [bullet k]
;;     (with-cmpt bullet [rb Rigidbody2D]
;;       (move-forward rb 0.2))))

;; ;; ------------------------------------------------------------
;; ;; bullet roles

;; (def bullet-roles
;;   {::movement bullet-movement-role
;;    ::lifespan lifespan-role 
;;    ::collision bullet-collision})

;; ;; ------------------------------------------------------------
;; ;; shooting
;; ;; hm actually this is more of a functional thing than a role

;; (defn shoot [start bearing]
;;   (let [bullet (GameObject/Instantiate (Resources/Load "missile" GameObject))]
;;     (with-cmpt bullet [rb Rigidbody2D
;;                        tr Transform]
;;       (set! (.position tr) (v3 (.x start) (.y start) 1))
;;       (.MoveRotation rb bearing))
;;     (roles+ bullet
;;       (-> bullet-roles
;;           (assoc-in [::lifespan :state :start] System.DateTime/Now)
;;           (assoc-in [::lifespan :state :lifespan] 2000)))
;;     bullet))

;; (defn shooter-shoot [obj]
;;   (with-cmpt obj [rb Rigidbody2D]
;;     (let [bullet (shoot (.position rb) (.rotation rb))]
;;       (do-ignore-collisions (cmpt obj Collider2D) (cmpt bullet Collider2D))
;;       bullet)))a

;; ;; ============================================================
;; ;; player

;; ;; ------------------------------------------------------------
;; ;; player movement

;; (defn player-movement-fixed-update [obj k]
;;   (with-cmpt obj [rb Rigidbody2D]
;;     (when (wasd-key)
;;       (.MoveRotation rb (abs-angle (controller-vector)))
;;       (set! (.angularVelocity rb) 0)
;;       (.AddForce rb
;;         (v2* (bearing-vector (.rotation rb))
;;           3)))))

;; (def player-movement-role
;;   {:fixed-update #'player-movement-fixed-update})

;; ;; ------------------------------------------------------------
;; ;; player shooting

;; (defrole player-shooting-role
;;   (update [obj k]
;;     (with-cmpt obj [rb Rigidbody2D]
;;       (when (Input/GetKeyDown "space")
;;         (shooter-shoot obj)))))

;; ;; ------------------------------------------------------------
;; ;; player roles
;; ;; maybe punt more

;; (def player-roles
;;   {::shooting player-shooting-role
;;    ::movement player-movement-role
;;    ::health (update health-role :state assoc :health 10)})

;; ;; ============================================================
;; ;; enemy

;; ;; ------------------------------------------------------------
;; ;; enemy shooting

;; (defrole enemy-shooting-role
;;   :state {:last-shot System.DateTime/Now}
;;   (update [obj k]
;;     (let [{:keys [target last-shot]} (state obj k)
;;           now System.DateTime/Now]
;;       (when (and target ;; this is stupid
;;                  (not (null-obj? target))
;;                  (< 1000 (.TotalMilliseconds (.Subtract now last-shot))))
;;         (update-state obj k assoc :last-shot now)
;;         (shooter-shoot obj)))))

;; ;; ------------------------------------------------------------
;; ;; enemy movement

;; (defrole enemy-movement-role
;;   :state {:target nil}
;;   (fixed-update [obj k]
;;     (let [{:keys [target]} (state obj k)]
;;       (when (not (null-obj? target))
;;         (with-cmpt obj [rb1 Rigidbody2D]
;;           (with-cmpt target [rb2 Rigidbody2D]
;;             (let [pos-diff (v2- (.position rb2) (.position rb1))
;;                   rot-diff (Vector2/SignedAngle
;;                              (bearing-vector (.rotation rb1))
;;                              pos-diff)]
;;               (.MoveRotation rb1
;;                 (+ (.rotation rb1)
;;                    (Mathf/Clamp -1 rot-diff 1))))))))))

;; ;; ------------------------------------------------------------
;; ;; enemy roles

;; (def enemy-roles
;;   {::shooting enemy-shooting-role
;;    ::movement enemy-movement-role
;;    ::health (update health-role :state assoc :health 10)})

;; (defn make-enemy [protagonist]
;;   (let [enemy (GameObject/Instantiate (Resources/Load "villain" GameObject))]
;;     (roles+ enemy
;;       (-> enemy-roles
;;           (assoc-in [::movement :state :target] protagonist)))))

;; ;; ============================================================
;; ;; player

;; ;; Other solutions exist.
;; (defonce player-atom
;;   (atom nil))

;; ;; ============================================================
;; ;; setup

;; (defn setup []
;;   (let [bullets (UnityEngine.LayerMask/NameToLayer "bullets")]
;;     (Physics2D/IgnoreLayerCollision (int bullets) (int bullets) true))
;;   (when @player-atom (retire @player-atom))
;;   (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
;;     (set! (.name player) "player")
;;     (roles+ player player-roles)
;;     (make-enemy player)
;;     (reset! player-atom player)))

