(ns masyst.cljc.schema
  (:require [schema.core :as s]))

(def Ciselnik {(s/optional-key :db/id) s/Int
               :ent/type s/Keyword
               :ent/title s/Str
               (s/optional-key :-errors) {s/Keyword s/Str}
               (s/optional-key :ebs/code) s/Str})

(def File {:db/id s/Int
           :ent/type s/Keyword
           :file/content-type s/Str
           :file/orig-name s/Str
           :file/size s/Int
           :file/server-name s/Str
           :file/parent {:db/id s/Int}
           (s/optional-key :file/view-count) s/Int
           :-created s/Inst})

(def Role {(s/optional-key :db/id) s/Int
           :ent/type s/Keyword
           :ent/title s/Str
           :role/right #{s/Keyword}})

(def User {(s/optional-key :db/id) s/Int
           :ent/type s/Keyword
           :ent/title s/Str
           (s/optional-key :user/email) s/Str
           :user/login s/Str
           (s/optional-key :user/passwd) s/Str
           :user/roles s/Str
           (s/optional-key :-rights) #{s/Keyword}
           (s/optional-key :user/login-count) s/Int})

(def Order {(s/optional-key :db/id) s/Int
            :ent/type s/Keyword
            :ent/title s/Str
            :order/date s/Inst
            :order/supplier Ciselnik
            (s/optional-key :ent/annotation) s/Str
            (s/optional-key :file/_parent) [File]
            (s/optional-key :-file) s/Any})

(def TechDesign {(s/optional-key :db/id) s/Int
                 :ent/type s/Keyword
                 :ent/title s/Str
                 :tech-design/code s/Str
                 :tech-design/material Ciselnik
                 (s/optional-key :tech-design/refs) [{:db/id s/Int}]
                 (s/optional-key :ent/annotation) s/Str
                 (s/optional-key :file/_parent) [File]
                 (s/optional-key :-file) s/Any})

(def Ebs {(s/optional-key :db/id) s/Int
          :ent/type s/Keyword
          :ent/title s/Str
          :ebs/code-ref Ciselnik
          (s/optional-key :ent/annotation) s/Str
          (s/optional-key :file/_parent) [File]
          (s/optional-key :-file) s/Any
          (s/optional-key :ebs-calc/price) s/Int
          (s/optional-key :ebs-calc/paid) s/Int
          (s/optional-key :ebs-offer/winner?) s/Bool})

(def Invoice {(s/optional-key :db/id) s/Int
              :ent/type s/Keyword
              :ent/title s/Str
              :file/_parent [File]
              (s/optional-key :-file) s/Any
              :ent/code s/Str
              :invoice/variable-symbol s/Int
              :ent/date s/Inst
              :ent/supplier {:db/id s/Int
                                 (s/optional-key :ent/title) s/Str
                                 (s/optional-key :ent/type) s/Keyword}
              :invoice/price s/Any
              (s/optional-key :invoice/paid) s/Any
              (s/optional-key :ent/annotation) s/Str
              (s/optional-key :invoice/checked) s/Bool
              (s/optional-key :invoice/text) s/Str
              (s/optional-key :invoice/due-date) s/Inst
              (s/optional-key :ent/cost-center) {:db/id s/Int
                                                     (s/optional-key :ent/title) s/Str
                                                     (s/optional-key :ent/type) s/Keyword}})


(def AppDb
  {:current-page s/Keyword
   (s/optional-key :auth-user) User
   :offline? s/Bool
   (s/optional-key :mis) s/Any

   (s/optional-key :msg) {(s/optional-key :error) (s/maybe s/Str)
                          (s/optional-key :info) (s/maybe s/Str)}
   (s/optional-key :ciselnik) {s/Keyword [Ciselnik]}
   (s/optional-key :ciselnik-new) {s/Keyword Ciselnik}
   (s/optional-key :entity-edit) {s/Keyword {:id (s/maybe s/Int)
                                             :edit? s/Bool}}
   (s/optional-key :table-states) {s/Any {:order-by s/Int
                                          :desc? s/Bool
                                          :search-all s/Str
                                          :search-colls {s/Int s/Str}
                                          (s/optional-key :selected-row-id) (s/maybe s/Int)
                                          :rows-per-page s/Int
                                          :page-no s/Int
                                          (s/optional-key :row-states) {s/Int {:checked? s/Bool}} }}
   (s/optional-key :user) {s/Int User}
   (s/optional-key :role) {s/Int Role}
   (s/optional-key :tech-design) {s/Int TechDesign}
   (s/optional-key :order) {s/Int Order}
   (s/optional-key :ebs-project) {s/Int Ebs}
   (s/optional-key :ebs-calc) {s/Int Ebs}
   (s/optional-key :ebs-offer) {s/Int Ebs}
   (s/optional-key :ebs-other) {s/Int Ebs}
   (s/optional-key :invoice) {s/Int Invoice}})

