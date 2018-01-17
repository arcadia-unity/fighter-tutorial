# Arcadia for Clojure Programmers

This tutorial assumes familiarity with Clojure, and a basic understanding of Unity's scene graph and messaging system.

This is bare-bones, out-of-the-box Arcadia. Many libraries exist that extend its functionality further.

## Project Setup

This tutorial assumes Unity version 2017.2.0f3, but should work in other recent versions. To check your Unity version, go to `Unity > About Unity` in the editor menubar.

1. Open Unity and create a new Unity project in 2D mode. The name doesn't matter, but for clarity here we'll refer to it as `fighter-tutorial`.
2. `cd` into `fighter-tutorial/Assets`.
3. `git clone https://github.com/arcadia-unity/fighter-tutorial.git .`
4. `git submodule init`
5. `git submodule update`

Tab into Unity (or open it if it was closed). Arcadia will load.

1. Once Arcadia has loaded, in the editor menubar select `Arcadia > Build > Internal Namespaces`. This will compile the core Arcadia namespaces for faster startup times.
2. Open the fighter tutorial scene by going to `File > Open Scene` and selecting `fighter.unity`.
3. Press the play button at the top of the editor.
4. Connect to Arcadia using your favorite editor ([instructions here](https://github.com/arcadia-unity/Arcadia/wiki/REPL)).

If you forgot to create the new project in 2D mode, press the `2D` button in the Scene view.

## Overview of the Arcadia Role System

Arcadia provides an opt-in bridge to the scene graph, representing bundles of state and behavior as persistent maps called 'roles'. The `:state` entry holds data, and the other entries associate Unity [messages](https://docs.unity3d.com/Manual/EventFunctions.html) (also known as "event functions") with `IFn` instances, encoded as `:update`, `:fixed-update`, `:on-collision-enter`, etc (the complete list can be found in the `arcadia.core/hook-types` map). When a message is dispatched to the GameObject, any `IFn`s associated with that message via an attached role will be called.

Here's an example of a role:

```clojure
(def example-role
  {:state {:health 10} ; data to attach to the object
   :update #'some-update-function ; var, the function value of which will run during the Update message
   :on-collision-enter #'some-collision-function} ; var, the function value of which will run during a collision event
   )
```

This role would be attached to a GameObject `obj` like this:

```clojure
(role+ obj ::example-role example-role)
```

Here the keys `:update` and `:on-collision-enter` correspond to the [Update](https://docs.unity3d.com/ScriptReference/MonoBehaviour.OnCollisionEnter.html) and [OnCollisionEnter](https://docs.unity3d.com/ScriptReference/MonoBehaviour.OnCollisionEnter.html) Unity messages. Their values are Clojure vars that will be invoked in response to those Unity messages. The value associated with `:update`, in this case the var `#'some-update-function`, will run every frame, because Unity triggers the `Update` message every frame. Similarly, the the value associated with `:on-collision-enter`, here the var `#'some-collision-function`, will run when any GameObject this role is attached to collides with something. The keyword `::example-role` in `role+` is called the _role key_, and is used to look up the `:state` associated with this particular role.

Anything implementing the `IFn` interface for the correct arity is supported as a value for the message entries; that is,

```clojure
{:update (fn [obj k] (UnityEngine.Debug/Log "running update"))}
```

would also work. Clojure vars, which also implement `IFn`, are greatly preferred, however, because they can be dynamically redefined from the REPL, and can [_serialize_](https://docs.unity3d.com/560/Documentation/Manual/script-Serialization.html).

The parameters expected of the `IFn` associated with a key are determined by the parameters expected of the corresponding Unity event function. A callback should have the same parameters as the corresponding Unity event function, plus an additional first parameter for the GameObject itself, and an additional final parameter for the key.

For example, the signature of the [OnCollisionEnter](https://docs.unity3d.com/ScriptReference/MonoBehaviour.OnCollisionEnter.html) message is

```
GameObject.OnCollisionEnter(Collision)
```

The signature of a function associated with the OnCollisionEnter message via `:on-collision-enter` is therefore

```
(fn [^GameObject obj, ^UnityEngine.Collision collision, role-key] ...)
```

To take another example, the signature of the [Update](https://docs.unity3d.com/ScriptReference/MonoBehaviour.Update.html) message is

```
GameObject.Update()
```

That is, there are no parameters. The expected signature of a function associated with the Update message via `:update` is therefore

```
(fn [^GameObject obj, role-key] ...)
```

More documentation about the role system can be found [here](https://github.com/arcadia-unity/Arcadia/wiki/Using-Arcadia#hooks-and-state).

## Building the player's avatar

Starter code for the following walkthrough, with the completed file commented out, can be found at `fighter-tutorial/Assets/fighter_tutorial/core.clj`.

First, we define the `ns` form to set up the namespace.

```clojure
(ns fighter-tutorial.core
  (:use arcadia.core arcadia.linear)
  (:require [arcadia.sugar :as a]    ; For augmented destructuring and imperative code
            [arcadia.scene :as scn]) ; For keeping track of stuff we put in the scene
  (:import [UnityEngine Collider2D Physics ; Heavy interop...
            GameObject Input Rigidbody2D
            Vector2 Mathf Resources Transform
            Collision2D Physics2D]
           ArcadiaState)) ; Handles our state
```

Let's start by making an inert GameObject representing the player. We'll do this by [instantiating](https://docs.unity3d.com/Manual/InstantiatingPrefabs.html) the `"fighter"` [prefab](https://docs.unity3d.com/Manual/Prefabs.html).

```clojure
(defn setup []
  ;; `retire` any objects registered with the label `::player`, removing them from the scene
  (scn/retire ::player)
  ;; Load the "fighter" prefab into the scene graph
  (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
    ;; Register the player GameObject with the label `::player`
    (scn/register player ::player)
    ;; Set its name
    (set! (.name player) "player")))
```

After evaluating this code, run `(setup)` in the REPL. A new GameObject should appear, looking like this: ![]()

If we call `(setup)` multiple times at this point, the scene will seem to remain the same, but really we're destroying and recreating the player every time.

Now let's define some helper functions for input and math.

```clojure
(defn bearing-vector [angle]
  (let [angle (* Mathf/Deg2Rad angle)]
    (v2 (Mathf/Cos angle) (Mathf/Sin angle))))

(defn abs-angle [v]
  (* Mathf/Rad2Deg
     (Mathf/Atan2 (.y v) (.x v))))

(defn controller-vector []
 (v2 (Input/GetAxis "Horizontal")
     (Input/GetAxis "Vertical")))

(defn wasd-key []
  (or (Input/GetKey "w")
      (Input/GetKey "a")
      (Input/GetKey "s")
      (Input/GetKey "d")))
```

### Player Movement

Now we can write the interactive movement logic.

To review, Arcadia associates state and behavior with GameObjects using maps called _roles_. Roles are attached to GameObjects on a key called the _role key_.

Roles specify callbacks that run in response to Unity messages, as well as an optional `:state` entry that holds data.

```clojure
(defn player-movement-fixed-update [obj k] ; We'll only use the `obj` parameter
  (with-cmpt obj [rb Rigidbody2D]          ; Gets the Rigidbody2D component
    (when (wasd-key)                       ; Checks for WASD key
      (.MoveRotation rb (abs-angle (controller-vector))) ; Rotates towards key
      (set! (.angularVelocity rb) 0)
      (.AddForce rb                                      ; Moves forwards
        (v2* (bearing-vector (.rotation rb))
             3)))))

;; Associates the FixedUpdate Unity message with a var in a role map
(def player-movement-role
  {:fixed-update #'player-movement-fixed-update})
```

Roles, in turn, can be gathered together into maps, and the roles in these maps attached to GameObjects with `roles+`.

```clojure
;; Packages the role up in a map with a descriptive key
(def player-roles
  {::movement player-movement-role})
```

Note that there is no deep need to have a separate `player-movement-role` var, for our purposes here the following would work just as well:

```clojure
(def player-roles
  {::movement {:fixed-update #'player-movement-fixed-update}})
```

Finally, we modify the `setup` function to attach the state and behavior specified in `player-roles`. We'll continue to modify this function as we add features to the game.

```clojure
(defn setup []
  (scn/retire ::player)
  (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
    (scn/register player ::player)
    (set! (.name player) "player")
    (roles+ player player-roles))) ; NEW
```

From the REPL, call `(setup)` again. Back in the Unity Game view, the player should now be controllable using the `w` `a` `s` `d` keys.

## Bullets

Now let's shoot some bullets. We want the fighter to launch a bullet every time the player hits the space key. We also want to clean up bullets after a certain amount of time.

We'll need:

- A function that "shoots" a bullet, placing it in the scene graph
- A role that moves the bullet forward every physics frame
- A role that calls the shooting function when the player hits space
- A role responsible for removing bullets after a certain period of time.

Let's start with the time restriction, so bullets don't pile up.

We could define a role for this the way we've been doing, like so:

```clojure
(defn lifespan-update [obj k]
  (let [{:keys [start lifespan]} (state obj k)]
    (when (< lifespan (.TotalMilliseconds (.Subtract System.DateTime/Now start)))
      (retire obj))))

(def lifespan-role
  {:state {:start System.DateTime/Now
           :lifespan 0}
   :update #'lifespan-update})
```

This can get a little tedious, however. Arcadia provides a `defrole` macro to speed the process of defining roles.

```clojure
(defrole lifespan-role
  :state {:start System.DateTime/Now
          :lifespan 0}
  (update [obj k]
    (let [{:keys [start lifespan]} (state obj k)]
      (when (< lifespan (.TotalMilliseconds (.Subtract System.DateTime/Now start)))
        (retire obj)))))
```

We'll use `defrole` from now on.

We want to avoid bullet self-collision. In Unity we can do this by setting the `"bullets"` layer to avoid collisions with itself. Modify `setup` to do so:

```clojure
(defn setup []
  (let [bullets (UnityEngine.LayerMask/NameToLayer "bullets")]         ; NEW
    (Physics2D/IgnoreLayerCollision (int bullets) (int bullets) true)) ; NEW
  (when @player-atom (retire @player-atom))
  (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
    (set! (.name player) "player")
    (roles+ player player-roles)
    (reset! player-atom player)))
```

Bullet movement is just:

```clojure
(defrole bullet-movement-role
  (fixed-update [bullet k]
    (with-cmpt bullet [rb Rigidbody2D]
      (move-forward rb 0.2))))
```

Finally, the roles map for bullets:

```clojure
(def bullet-roles
  {::movement bullet-movement-role
   ::lifespan lifespan-role})
```

We would like to share the shooting logic with both the player and non-player entities. We'll use two functions, `shoot-bullet` and `shoot`. `shoot-bullet` takes a `UnityEngine.Vector2` starting position `start` and an angle `bearing`, and creates a new bullet at that position and angle, returning the bullet.

`shoot` takes a GameObject and shoots a bullet forward from it, set to ignore collisions with the GameObject itself.

```clojure
(defn shoot-bullet [start bearing]
  (a/let [bullet (GameObject/Instantiate
                   (Resources/Load "missile" GameObject))
          (a/with-cmpt rb Rigidbody2D, tr Transform) bullet]
    (scn/register bullet ::bullet)
    (set! (.position tr) (v3 (.x start) (.y start) 1))
    (.MoveRotation rb bearing)
    (roles+ bullet
      (-> bullet-roles
          (assoc-in [::lifespan :state :start] System.DateTime/Now)
          (assoc-in [::lifespan :state :lifespan] 2000)))
    bullet))

(defn shoot [obj layer]
  (with-cmpt obj [rb Rigidbody2D]
    (let [bullet (shoot-bullet (.position rb) (.rotation rb))]
      (set! (.layer bullet) layer)
      bullet)))
```

Now we give the player the ability to shoot bullets by hitting space:

```clojure
(defrole player-shooting-role
  (update [obj k]
    (with-cmpt obj [rb Rigidbody2D]
      (when (Input/GetKeyDown "space")
        (shoot obj player-bullets-layer)))))
```

We add this functionality to the player by going back and editing `player-roles`:

```clojure
(def player-roles
  {::movement player-movement-role
   ::shooting player-shooting-role}) ; NEW
```

## The enemy

We can create the enemy using the same technique: define roles, attach them to a GameObject. Note the reuse of `shoot`.

```clojure
;; enemy shooting
(defrole enemy-shooting-role
  :state {:last-shot System.DateTime/Now}
  (update [obj k]
    (let [{:keys [target last-shot]} (state obj k)
          now System.DateTime/Now]
      (when (and (obj-nil target) ; check that the target is neither nil nor a null object
                 (< 1000 (.TotalMilliseconds (.Subtract now last-shot))))
        (update-state obj k assoc :last-shot now)
        (shoot obj enemy-bullets-layer)))))

;; enemy movement
(defrole enemy-movement-role
  :state {:target nil}
  (fixed-update [obj k]
    ;; Make sure the target is neither nil nor the null object using
    ;; `obj-nil`
    (when-let [target (obj-nil (:target (state obj k)))]
      ;; We're going to use augmented destructuring to access
      ;; components of GameObjects using `arcadia.sugar/with-cmpt`,
      ;; and access object properties and fields using
      ;; `arcadia.sugar/o`. See the docs for `arcadia.sugar/let` for
      ;; more details.
      (a/let [(a/with-cmpt rb1 Rigidbody2D) obj                    ; Get the Rigidbody2D component
              (a/o pos1 position, rot1 rotation) rb1               ; Get the position and rotation from it
              (a/with-cmpt (a/o pos2 position) Rigidbody2D) target ; Get the position of the target's Rigidbody2D
              pos-diff (v2- pos2 pos1)                             ; Get the difference vector from the object to the target
              rot-diff (Vector2/SignedAngle                        ; Get the rotation needed to face the target
                         (bearing-vector rot1)
                         pos-diff)]
        (.MoveRotation rb1  ; rotate the Rigidbody2D towards the target, clamped to one degree per frame
          (+ rot1 (Mathf/Clamp -1 rot-diff 1)))))))

(def enemy-roles
 {::shooting enemy-shooting-role
  ::movement enemy-movement-role})

;; function to construct the enemy
(defn make-enemy [protagonist]
  (let [enemy (GameObject/Instantiate (Resources/Load "villain" GameObject))]
    (scn/register enemy ::enemy)
    (roles+ enemy
      (-> enemy-roles
          (assoc-in [::movement :state :target] protagonist)
          (assoc-in [::shooting :state :target] protagonist)))))
```

Now we can add the enemy to the `setup` function:

```clojure
(defn setup []
  (scn/retire ::enemy ::bullet ::player)
  (let [player (GameObject/Instantiate (Resources/Load "fighter"))]
    (scn/register player ::player)
    (set! (.name player) "player")
    (roles+ player player-roles)
    (make-enemy player))) ; NEW
```

### Damage

To represent characters capable of taking damage, we can add a `:health` key to state and some logic to remove a player when the health reaches zero. We also define a `damage` function that removes health from an entity.

```clojure
;; health, remove from scene when zero
;; expects to be keyed at ::health
(defrole health-role
  :state {:health 1}
  (update [obj k]
    (let [{:keys [health]} (state obj k)]
      (when (<= health 0)
        (retire obj)))))

(defn damage [obj amt]
  (update-state obj ::health update :health - amt))
```

We can then set a role for bullets by which they remove health from entities they collide with.

```clojure
(defrole bullet-collision
  (on-trigger-enter2d [bullet, ^Collider2D collider, k]
    (let [obj2 (.gameObject collider)]
      (when (state obj2 ::health)
        (damage obj2 1)
        (retire bullet)))))
```

Now we add this role to `bullet-roles`:

```clojure
(def bullet-roles
  {::movement bullet-movement-role
   ::lifespan lifespan-role
   ::collision bullet-collision}) ; NEW
```

To make the player susceptible to the enemy's bullets, we need only give it the `health-role`:

```clojure
(def player-roles
  {::movement player-movement-role
   ::shooting player-shooting-role
   ::health (update health-role :state assoc :health 10)}) ; NEW
```

To make the enemy susceptible to the player's bullets, we do the same:

```clojure
(def enemy-roles
  {::shooting enemy-shooting-role
   ::movement enemy-movement-role
   ::health (update health-role :state assoc :health 10)}) ; NEW
```
