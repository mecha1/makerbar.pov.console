(ns makerbar.pov.ui
  (:gen-class
    :extends processing.core.PApplet
    :methods [[captureEvent [processing.video.Capture] void]
              [movieEvent [processing.video.Movie] void]])
  (:import [processing.core PApplet])
  (:require [clojure.tools.cli :as cli]
            [makerbar.pov.console :as console]
            [makerbar.pov.controller.ddr :as ddr]
            [makerbar.pov.controller.keyboard :as k]
            [makerbar.pov.game :as game]
            [makerbar.pov.state :as s]
            [makerbar.pov.ui.draw :as d]
            [makerbar.pov.ui.processing :as p]))


(defn setup []
  (p/size (p/display-width) (p/display-height))
  ; (q/frame-rate 30)
  (d/init))

(defn -setup [this] (p/with-applet this (setup)))
;(defn -sketchFullScreen [this] true)
(defn -draw [this] (p/with-applet this (console/draw)))

(defn -keyPressed [this event] (p/with-applet this (k/key-pressed event)))

(defn -captureEvent [this camera] (.read camera))
(defn -movieEvent [this movie] (.read movie))

(defn -main
  [& args]

  (let [{{:keys [host port mirror]} :options}
        (cli/parse-opts args
                        [["-h" "--host HOST" "Host IP address"]
                         ["-p" "--port PORT" "Port number"
                          :default 10000
                          :parse-fn #(Integer/parseInt %)]
                         ["-m" "--mirror" "Mirror console display"]])]
    (if host
      (do
        (println "Connecting to Rendersphere at" (str host ":" port))
        (s/set-pov-addr! {:host host
                          :port port}))
      (println "Rendersphere connection not configured"))
    (if mirror (s/set-state! :console-mirror mirror))

    (let [ch (ddr/init-ddr)]
      (game/init-game ch)))

  (PApplet/main "makerbar.pov.ui"))