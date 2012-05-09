/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.providermanagement.fragment.controller;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.RelationshipType;
import org.openmrs.api.context.Context;
import org.openmrs.module.providermanagement.Provider;
import org.openmrs.module.providermanagement.ProviderManagementGlobalProperties;
import org.openmrs.module.providermanagement.ProviderManagementWebUtil;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.ProviderManagementService;
import org.openmrs.module.providermanagement.exception.PersonIsNotProviderException;
import org.openmrs.ui.framework.annotation.BindParams;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.MethodParam;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.action.SuccessResult;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProviderEditFragmentController {

    // simple command object for provider
    public class ProviderCommand {

        private String identifier;

        private ProviderRole providerRole;

        private Map<String, String> attributeMap = new HashMap<String, String>();

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public ProviderRole getProviderRole() {
            return providerRole;
        }

        public void setProviderRole(ProviderRole providerRole) {
            this.providerRole = providerRole;
        }

        public Map<String, String> getAttributeMap() {
            return attributeMap;
        }

        public void setAttributeMap(Map<String, String> attributeMap) {
            this.attributeMap = attributeMap;
        }
    }

    public void controller(PageModel sharedPageModel, FragmentModel model,
                           @FragmentParam(value = "person", required = false) Person personParam,
                           @FragmentParam(value = "personId", required = false) Integer personId) {

        // fetch the person and provider
        Person person = ProviderManagementWebUtil.getPerson(sharedPageModel, personParam, personId);
        Provider provider = null;

        if (person != null) {
            try {
                provider = ProviderManagementWebUtil.getProvider(person);
            }
            catch (PersonIsNotProviderException e) {
                // we are allowed to have persons who are not providers when in the "add" mode
            }
        }

        // add the person and the provider to the module
        model.addAttribute("person", person);
        model.addAttribute("provider", provider);

        // also add the person attribute types we want to display
        model.addAttribute("personAttributeTypes", ProviderManagementGlobalProperties.GLOBAL_PROPERTY_PERSON_ATTRIBUTE_TYPES());

        // add the possible provider roles
        model.addAttribute("providerRoles", Context.getService(ProviderManagementService.class).getAllProviderRoles(false));

    }


    /**
     * Initializes a person object for binding by adding empty person attributes as needed
     */
    public Person initializePerson(@RequestParam(value = "personId", required = false) Person person) {

        if (person == null) {
            person = new Person();
        }

        if (person.getPersonName() == null) {
            person.addName(new PersonName());
        }

        if (person.getPersonAddress() == null) {
            person.addAddress(new PersonAddress());
        }

        for (PersonAttributeType attributeType : ProviderManagementGlobalProperties.GLOBAL_PROPERTY_PERSON_ATTRIBUTE_TYPES()) {
            if (person.getAttribute(attributeType) == null) {
                PersonAttribute attr = new PersonAttribute(attributeType, null);
                attr.setPerson(person);
                // we have to do this manually, don't use person.addAttribute(), because that method ignores null attributes
                person.getAttributes().add(attr);
            }
        }

        return person;
    }

    public ProviderCommand initializeProviderCommand()  {
        return new ProviderCommand();
    }

    // TODO: unit test this!

    public void saveProvider(@MethodParam("initializePerson") @BindParams() Person person,
                             @MethodParam("initializeProviderCommand") @BindParams("provider") ProviderCommand providerCommand) {

        // TODO: also needs to hold state on validation failures
        // TODO: add validation via annotation when it works
        // TODO: should automatically redisplay edit fragment when validation fails (how to do this?)

        // fetch the provider associated with this person
        Provider provider;

        if (person.getId() != null) {     // make sure this isn't transient person we have just created
            try {
                provider = ProviderManagementWebUtil.getProvider(person);
            }
            catch (PersonIsNotProviderException e) {
                // we will get here if we are upgrading an existing person to a provider
                provider = new Provider();
            }
        }
        else {
            // we will get here if the creating an entirely new person from scratch
            provider = new Provider();
        }

        // need to manually bind the provider attributes
        provider.setIdentifier(providerCommand.getIdentifier());
        provider.setProviderRole(providerCommand.getProviderRole());

        // manually bind the provider attributes
        // TODO: double-check that this is working correctly
        // TODO: this currently only works for string attributes
        // TODO: do we need to make this work for integers since households are numbers?
        // TODO: note that when provider attributes are updated they are voided, but person attributes are overwritten?
        if (providerCommand.attributeMap != null) {
            for (Map.Entry entry : providerCommand.attributeMap.entrySet()) {

                if (StringUtils.isNotBlank(entry.getValue().toString())) {
                    ProviderAttributeType type = Context.getProviderService().getProviderAttributeType(Integer.valueOf(entry.getKey().toString()));

                    // NOTE: note that this currently allows only one active attribute of each type--and retires any others
                    boolean foundMatch = false;
                    for (ProviderAttribute attr : provider.getActiveAttributes(type)) {
                        if (attr.getValueReference().equals(entry.getValue())) {
                            foundMatch = true;
                        }
                        else {
                            attr.setVoided(true);
                            attr.setVoidReason("voided during provider management module provider update");
                        }
                    }

                    // sets the attribute if no existing match found
                    if (!foundMatch) {
                        ProviderAttribute attr = new ProviderAttribute();
                        attr.setAttributeType(type);
                        attr.setValueReferenceInternal(entry.getValue().toString());   // TODO: only works with string attributes
                        provider.addAttribute(attr);
                    }
                }
            }
        }


        // TODO: add provider validation?  should we warn/stop someone from changing a provider role if they have relationship types or supervisees not supported by the new role?
        // TODO: think about validation issues here... if we simply trap person validation and it fails, we would still want to be able to roll back provider information

        // TODO: should we remove the person address field if it is not used?

        // need to manually remove any person attributes that have no value
        for (PersonAttributeType attributeType : ProviderManagementGlobalProperties.GLOBAL_PROPERTY_PERSON_ATTRIBUTE_TYPES()) {
            if (person.getAttribute(attributeType) != null  && StringUtils.isBlank(person.getAttribute(attributeType).getValue())) {
                person.removeAttribute(person.getAttribute(attributeType));
            }
        }

        // save the person and the provider
        Context.getPersonService().savePerson(person);

        // if this is new person & provider, we may not set have set the person on the provider
        provider.setPerson(person);
        Context.getProviderService().saveProvider(provider);
    }

    public FragmentActionResult addSupervisee(@RequestParam(value = "supervisor", required = true) Person supervisor,
                                              @RequestParam(value = "supervisee", required=true) Person supervisee) {

        // TODO: better handle error cases
        try {
            Context.getService(ProviderManagementService.class).assignProviderToSupervisor(supervisee, supervisor);
            return new SuccessResult();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public FragmentActionResult removeSupervisees(@RequestParam(value = "supervisor", required = true) Person supervisor,
                                                  @RequestParam(value = "supervisees", required = true) List<Person> supervisees) {

        // TODO: better handle error cases
        try {
            for (Person supervisee : supervisees) {
                Context.getService(ProviderManagementService.class).unassignProviderFromSupervisor(supervisee, supervisor);
            }
            return new SuccessResult();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public FragmentActionResult addPatient(@RequestParam(value = "provider", required = true) Person provider,
                                           @RequestParam(value = "relationshipType", required = true) RelationshipType relationshipType,
                                           @RequestParam(value = "patient", required = true) Patient patient) {

        // TODO: better handle error cases
        try {
            Context.getService(ProviderManagementService.class).assignPatientToProvider(patient, provider, relationshipType);
            return new SuccessResult();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public FragmentActionResult removePatients(@RequestParam(value = "provider", required = true) Person provider,
                                                @RequestParam(value = "relationshipType", required = true) RelationshipType relationshipType,
                                                @RequestParam(value = "patients", required = true) List<Patient> patients) {

        // TODO: better handle error cases
        try {
            for (Patient patient : patients) {
                Context.getService(ProviderManagementService.class).unassignPatientFromProvider(patient, provider, relationshipType);
            }
            return new SuccessResult();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
