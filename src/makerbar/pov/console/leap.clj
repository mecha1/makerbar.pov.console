(ns makerbar.pov.console.leap
  (:import [com.leapmotion.leap CircleGesture Controller Gesture Gesture$Type Listener SwipeGesture])
  (:require [clojure.core.async :as async :refer (<!! >!! go go-loop put!)]
            [clojure.math.numeric-tower :as math :refer (expt sqrt)]
            [makerbar.pov.console.images :as i]
            [makerbar.pov.console.state :as s]))


(def start-scale-frame (atom nil))
(def start-pan (atom nil))


(defn duration
  [frame0 frame1]
  (- (.timestamp frame1) (.timestamp frame0)))

(defn as-vec
  [v]
  [(.getX v) (.getY v) (.getZ v)])

(defn delta
  [v0 v1]
  [(- (.getX v1) (.getX v0))
   (- (.getY v1) (.getY v0))
   (- (.getZ v1) (.getZ v0))])

; Console functions

(defn fade-console
  ([fade-in]
    (let [fade (s/get-state :console-fade)]
      (if fade-in
        (fade-console fade 0)
        (fade-console fade 200))))
  ([from to]
    (let [step (/ (- to from) 10)]
      (doseq [fade (range from (+ to step) step)]
        (s/set-state! :console-fade (float fade))
        (<!! (async/timeout 50))))))

(defn init-leap []
  (let [controller (Controller.)
        fade-ch (async/chan (async/sliding-buffer 1))
        listener (proxy [Listener] []
                   (onInit [controller]
                     (println "Leap initialized"))
                   (onConnect [controller]
                     (println "Leap connected")
                     (.enableGesture controller Gesture$Type/TYPE_SWIPE)
                     #_(.enableGesture controller Gesture$Type/TYPE_CIRCLE)
                     (let [config (.config controller)]
                       (if (and
                              (.setFloat config "Gesture.Swipe.MinLength" 200.0)
                              (.setFloat config "Gesture.Swipe.MinVelocity" 750.0)
                              (.setFloat config "Gesture.Circle.MinRadius" 10.0)
                              (.setFloat config "Gesture.Circle.MinArc" 0.5))
                         (.save config))))
                   (onDisconnect [controller]
                     (println "Leap disconnected"))
                   (onExit [controller]
                     (println "Leap exited"))
                   (onFrame [controller]
                     (let [frame (.frame controller)
                           hands (.hands frame)
                           num-hands (.count hands)]
                       (if (< 0 num-hands)
                         (put! fade-ch true)
                         (put! fade-ch false))
                         
                         (condp = num-hands
                           2 (let [left-hand (.leftmost hands)
                                   right-hand (.rightmost hands)]
                               #_(if (and (= 0 (-> left-hand .fingers .count))
                                          (= 0 (-> right-hand .fingers .count)))
                                   (if (nil? @start-scale-frame)
                                     (do (println "start scale") (reset! start-scale-frame frame))
                                     (do
                                       (println "scale" (.scaleFactor frame @start-scale-frame))
                                       (s/set-state! :img-scale (* (s/get-state :img-scale) (.scaleFactor frame @start-scale-frame)))))
                                   (reset! start-scale-frame nil)))
                           1 (do
                               (let [hand (.get hands 0)
                                     num-fingers (-> hand .fingers .count)]
                                 (if (<= num-fingers 1)
                                   (if (nil? @start-pan)
                                     (reset! start-pan {:start-frame frame
                                                        :img-offset (s/get-state :img-offset)})
                                     (let [{start-frame :start-frame
                                            [x0 y0] :img-offset} @start-pan
                                           [dx dy dz] (as-vec (.translation hand start-frame))
                                           box (.interactionBox frame)]
                                       (s/set-state! :img-offset [(+ x0 (* s/pov-width (/ dx (.width box)))) (+ y0 (- (* s/pov-height (/ dz (.height box)))))])))
                                   (reset! start-pan nil)))
                               (doseq [gesture (.gestures frame)]
                                 (condp = (.type gesture)
                                   Gesture$Type/TYPE_SWIPE (let [swipe (SwipeGesture. gesture)]
                                                             (println "swipe" (.direction swipe)))
                                   Gesture$Type/TYPE_CIRCLE (let [circle (CircleGesture. gesture)
                                                                  normal-z (.getZ (.normal circle))
                                                                  duration (.durationSeconds circle)]
                                                              (if (< 0.5 duration)
                                                                (println "circle" (if (< 0 normal-z) "counterclockwise" "clockwise") duration))))))
                           nil))))
        control-ch (async/chan)]
    (.addListener controller listener)
    
    (go-loop [[val ch] (alts! [control-ch fade-ch])]
             (condp = ch
               fade-ch (do
                         (fade-console val)
                         (recur (alts! [control-ch fade-ch]))))
             control-ch (.removeListener controller listener))
    
    control-ch))