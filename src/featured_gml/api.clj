(ns featured-gml.api
  (:require [featured-gml.runner :as runner]
            [ring.util.response :as r]
            [ring.middleware.json :refer [wrap-json-response]]
            [clj-time [core :as t] [local :as tl]]
            [compojure.core :refer :all]
            [compojure.route :as route])
  (:gen-class))

(extend-protocol cheshire.generate/JSONable
  org.joda.time.DateTime
  (to-json [t jg] (.writeString jg (str t))))

(defroutes app-routes
    (context "/api" []
             (GET "/info" [] (r/response {:version (runner/implementation-version)}))
             (GET "/ping" [] (r/response {:pong (tl/local-now)})))
    (route/not-found "NOT FOUND"))

(def app (wrap-json-response app-routes))
