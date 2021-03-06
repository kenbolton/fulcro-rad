(ns com.fulcrologic.rad.rendering.semantic-ui.form
  (:require
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.fulcro-i18n.i18n :as i18n :refer [tr]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    #?(:cljs
       [com.fulcrologic.fulcro.dom :as dom]
       :clj
       [com.fulcrologic.fulcro.dom-server :as dom])
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]))

(defn render-to-many [{::form/keys [form-instance] :as env} {k ::attr/qualified-key :as attr} {::form/keys [subforms] :as options}]
  (let [{::form/keys [ui can-delete-row? can-add-row?]} (get subforms k)
        parent      (comp/props form-instance)
        can-delete? (fn [item] (can-delete-row? parent item))
        items       (-> form-instance comp/props k)
        ui-factory  (comp/computed-factory ui {:keyfn (fn [item] (-> ui (comp/get-ident item) second str))})]
    (dom/div :.ui.basic.segment {:key (str k)}
      (dom/h3 "Addresses")
      (mapv
        (fn [props]
          (ui-factory props
            (merge
              env
              {::form/parent          form-instance
               ::form/parent-relation k
               ::form/can-delete?     (if can-delete-row? can-delete? false)})))
        items)
      (when (and can-add-row? (can-add-row? parent))
        (dom/div :.ui.basic.segment
          (dom/button :.ui.icon.button
            {:onClick (fn [_]
                        (form/add-child! (assoc env
                                           ::form/parent-relation k
                                           ::form/parent form-instance
                                           ::form/child-class ui)))}
            (dom/i :.plus.icon)))))))

(defn render-to-one [{::form/keys [form-instance] :as env} {k ::attr/qualified-key :as attr} {::form/keys [subforms] :as options}]
  (let [{::form/keys [ui can-delete-row?]} (get subforms k)
        parent     (comp/props form-instance)
        props      (-> form-instance comp/props k)
        ui-factory (comp/computed-factory ui)]
    (when props
      (ui-factory props (merge
                          env
                          {::form/nested?         true
                           ::form/parent          form-instance
                           ::form/parent-relation k
                           ::form/can-delete?     (if can-delete-row?
                                                    (partial can-delete-row? parent)
                                                    false)})))))

(defn render-ref [env {::attr/keys [cardinality] :as attr} options]
  (if (= :many cardinality)
    (render-to-many env attr options)
    (render-to-one env attr options)))

(defn render-attribute [env attr {::form/keys [subforms] :as options}]
  (let [{k ::attr/qualified-key} attr]
    (if (contains? subforms k)
      (render-ref env attr options)
      (form/render-field env attr))))

(def n->str {1 "one"
             2 "two"
             3 "three"
             4 "four"
             5 "five"
             6 "six"
             7 "seven"})

(def attribute-map (memoize
                     (fn [attributes]
                       (reduce
                         (fn [m {::attr/keys [qualified-key] :as attr}]
                           (assoc m qualified-key attr))
                         {}
                         attributes))))

(defn render-layout [env {::form/keys [attributes layout] :as options}]
  (let [k->attribute (attribute-map attributes)]
    (map-indexed
      (fn [idx row]
        (dom/div {:key idx :className (str (n->str (count row)) " fields")}
          (mapv (fn [col]
                  (enc/if-let [_    k->attribute
                               attr (k->attribute col)]
                    (render-attribute env attr options)
                    (log/error "Missing attribute (or lookup) for" col)))
            row)))
      layout)))

(defn ui-render-layout [{::form/keys [props computed-props form-instance master-form] :as env}]
  (let [{::form/keys [can-delete?]} computed-props
        nested? (not= master-form form-instance)
        {::form/keys [attributes layout] :as options} (comp/component-options form-instance)
        dirty?  (or (:ui/new? props) (fs/dirty? props))]
    (if nested?
      (dom/div :.ui.form
        (dom/div :.ui.segment
          (when can-delete?
            (dom/button :.ui.icon.primary.right.floated.button {:disabled (not (can-delete? props))
                                                                :onClick  (fn []
                                                                            (form/delete-child! env))}
              (dom/i :.times.icon)))
          (if layout
            (render-layout env options)
            (mapv
              (fn [attr] (render-attribute env attr options))
              attributes))))
      (dom/div :.ui.form
        (dom/div :.ui.top.attached.segment
          (dom/h3 :.ui.header (or (some-> form-instance comp/component-options ::form/title i18n/tr-unsafe) (tr "Edit"))))
        (dom/div :.ui.attached.segment
          (if layout
            (render-layout env (merge options computed-props {::form/nested? true}))
            (mapv
              (fn [attr] (render-attribute env attr options))
              attributes)))
        (dom/div :.ui.bottom.attached.segment
          (dom/button :.ui.secondary.button {:disabled (not dirty?)
                                             :onClick  (fn [] (form/undo-all! env))} (tr "Undo"))
          (dom/button :.ui.secondary.button {:onClick (fn [] (form/cancel! env))} (tr "Cancel"))
          (dom/button :.ui.primary.button {:disabled (not dirty?)
                                           :onClick  (fn [] (form/save! env))} (tr "Save")))))))

