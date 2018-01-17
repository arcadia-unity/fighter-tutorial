(ns fighter-tutorial.core
  (:use arcadia.core arcadia.linear)
  (:require [arcadia.sugar :as a]    ; For augmented destructuring and imperative code
            [arcadia.scene :as scn]) ; For keeping track of stuff we put in the scene
  (:import [UnityEngine Collider2D Physics ; Heavy interop...
            GameObject Input Rigidbody2D
            Vector2 Mathf Resources Transform
            Collision2D Physics2D]
           ArcadiaState)) ; Handles our state

;; ;; The following is completed code for the tutorial.
;; ;; You can follow along by either uncommenting chunks of code as the
;; ;; tutorial gets to them, or writing your own in step with the
;; ;; tutorial. To see the full game, uncomment everything below and
;; ;; run (setup) from the repl.

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
;; ;; layers

;; (def player-bullets-layer (UnityEngine.LayerMask/NameToLayer "player-bullets"))

;; (def enemy-bullets-layer (UnityEngine.LayerMask/NameToLayer "enemy-bullets"))

;; ;; ============================================================
;; ;; bullet

;; ;; ------------------------------------------------------------
;; ;; bullet collision

;; (defrole bullet-collision
;;   (on-trigger-enter2d [bullet, ^Collider2D collider, k]
;;     (let [obj2 (.gameObject collider)]
;;       (when (state obj2 ::health)
;;         (damage obj2 1)
;;         (retire bullet)))))

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

;; (defn shoot [start bearing]
;;   (a/let [bullet (GameObject/Instantiate
;;                    (Resources/Load "missile" GameObject))
;;           (a/with-cmpt rb Rigidbody2D, tr Transform) bullet]
;;     (scn/register bullet ::bullet)
;;     (set! (.position tr) (v3 (.x start) (.y start) 1))
;;     (.MoveRotation rb bearing)
;;     (roles+ bullet
;;       (-> bullet-roles
;;           (assoc-in [::lifespan :state :start] System.DateTime/Now)
;;           (assoc-in [::lifespan :state :lifespan] 2000)))
;;     bullet))

;; (defn shooter-shoot [obj layer]
;;   (with-cmpt obj [rb Rigidbody2D]
;;     (let [bullet (shoot (.position rb) (.rotation rb))]
;;       (set! (.layer bullet) layer)
;;       bullet)))

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
;;         (shooter-shoot obj player-bullets-layer)))))

;; ;; ------------------------------------------------------------
;; ;; player roles

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
;;       (when (and (obj-nil target) ; check that the target is neither nil nor a null object
;;                  (< 1000 (.TotalMilliseconds (.Subtract now last-shot))))
;;         (update-state obj k assoc :last-shot now)
;;         (shooter-shoot obj enemy-bullets-layer)))))

;; ;; ------------------------------------------------------------
;; ;; enemy movement

;; (defrole enemy-movement-role
;;   :state {:target nil}
;;   (fixed-update [obj k]
;;     ;; Make sure the target is neither nil nor the null object using
;;     ;; `obj-nil`
;;     (when-let [target (obj-nil (:target (state obj k)))] 
;;       ;; We're going to use augmented destructuring to access
;;       ;; components of GameObjects using `arcadia.sugar/with-cmpt`,
;;       ;; and access object properties and fields using
;;       ;; `arcadia.sugar/o`. See the docs for `arcadia.sugar/let` for
;;       ;; more details.
;;       (a/let [(a/with-cmpt rb1 Rigidbody2D) obj                    ; Get the Rigidbody2D component
;;               (a/o pos1 position, rot1 rotation) rb1               ; Get the position and rotation from it
;;               (a/with-cmpt (a/o pos2 position) Rigidbody2D) target ; Get the position of the target's Rigidbody2D
;;               pos-diff (v2- pos2 pos1)                             ; Get the difference vector from the object to the target
;;               rot-diff (Vector2/SignedAngle                        ; Get the rotation needed to face the target
;;                          (bearing-vector rot1)
;;                          pos-diff)]
;;         (.MoveRotation rb1  ; rotate the Rigidbody2D towards the target, clamped to one degree per frame
;;           (+ rot1 (Mathf/Clamp -1 rot-diff 1)))))))

;; ;; ------------------------------------------------------------
;; ;; enemy roles

;; (def enemy-roles
;;   {::shooting enemy-shooting-role
;;    ::movement enemy-movement-role
;;    ::health (update health-role :state assoc :health 10)})

;; (defn make-enemy [protagonist]
;;   (let [enemy (GameObject/Instantiate (Resources/Load "villain" GameObject))]
;;     (scn/register enemy ::enemy)
;;     (roles+ enemy
;;       (-> enemy-roles
;;           (assoc-in [::movement :state :target] protagonist)
;;           (assoc-in [::shooting :state :target] protagonist)))))

;; ;; ============================================================
;; ;; setup

;; (defn setup []
;;   (scn/retire ::enemy ::bullet ::player) ;; i suppose this should be retire-or
;;   (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
;;     (scn/register player ::player)
;;     (set! (.name player) "player")
;;     (roles+ player player-roles)
;;     (make-enemy player)))

