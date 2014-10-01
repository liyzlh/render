package org.janelia.alignment.spec;

import mpicbg.trakem2.transform.AffineModel2D;
import org.janelia.alignment.json.JsonUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the {@link org.janelia.alignment.TileSpec} class.
 *
 * @author Eric Trautman
 */
public class TransformSpecTest {

    private LeafTransformSpec leaf1;
    private LeafTransformSpec leaf2;
    private LeafTransformSpec leaf3;
    private ReferenceTransformSpec ref1;
    private ReferenceTransformSpec ref99;
    private ListTransformSpec listSpec;

    @Before
    public void setUp() throws Exception {
        final TransformSpecMetaData lcMetaData = new TransformSpecMetaData();
        lcMetaData.setGroup("test");

        leaf1 = new LeafTransformSpec("1", lcMetaData, AFFINE_2D, "1  0  0  1  0  0");
        leaf2 = new LeafTransformSpec("2", null, AFFINE_2D, "2  0  0  2  0  0");
        leaf3 = new LeafTransformSpec("3", lcMetaData, AFFINE_2D, "3  0  0  3  0  0");

        ref1 = new ReferenceTransformSpec(leaf1.getId());
        ref99 = new ReferenceTransformSpec("99");

        final ListTransformSpec list4 = new ListTransformSpec("4", null);
        list4.addSpec(leaf1);
        list4.addSpec(ref99);

        final InterpolatedTransformSpec interpolated5 = new InterpolatedTransformSpec("5", null, ref1, list4, 2.3f);

        listSpec = new ListTransformSpec("6", null);
        listSpec.addSpec(leaf3);
        listSpec.addSpec(interpolated5);
    }

    @Test
    public void testJsonProcessing() throws Exception {

        final String json = listSpec.toJson();

        Assert.assertNotNull("json generation returned null string", json);

        LOG.info("generated:\n" + json);

        final TransformSpec parsedSpec = JsonUtils.GSON.fromJson(json, TransformSpec.class);

        Assert.assertNotNull("null spec returned from json parse");

        ListTransformSpec parsedListSpec = null;
        if (parsedSpec instanceof ListTransformSpec) {
            parsedListSpec = (ListTransformSpec) parsedSpec;
        } else {
            Assert.fail("returned " + parsedSpec.getClass() + " instance instead of " +
                        ListTransformSpec.class.getName() + " instance");
        }

        Assert.assertEquals("top level list has incorrect size", listSpec.size(), parsedListSpec.size());

        final TransformSpec parsedListSpecItem0 = parsedListSpec.getSpec(0);

        LeafTransformSpec parsedLeafSpec = null;
        if (parsedListSpecItem0 instanceof LeafTransformSpec) {
            parsedLeafSpec = (LeafTransformSpec) parsedListSpecItem0;
        } else {
            Assert.fail("returned " + parsedListSpecItem0.getClass() + " instance instead of " +
                        LeafTransformSpec.class.getName() + " instance");
        }

        TransformSpecMetaData parsedMetaData = parsedLeafSpec.getMetaData();
        Assert.assertNotNull("null meta data returned", parsedMetaData);

        TransformSpecMetaData lcMetaData = leaf3.getMetaData();
        Assert.assertEquals("invalid meta data group",
                            lcMetaData.getGroup(), parsedMetaData.getGroup());

        Assert.assertFalse("parsed spec should not be fully resolved", parsedSpec.isFullyResolved());

        validateUnresolvedSize("after parse", parsedSpec, 2);
    }

    @Test
    public void testResolveReferences() throws Exception {

        validateUnresolvedSize("before resolution", listSpec, 2);

        Map<String, TransformSpec> idToSpecMap = new HashMap<String, TransformSpec>();
        idToSpecMap.put(ref1.getRefId(), leaf1);
        idToSpecMap.put(ref99.getRefId(), new ReferenceTransformSpec(leaf2.getId()));

        listSpec.resolveReferences(idToSpecMap);

        validateUnresolvedSize("after first resolution", listSpec, 1);

        idToSpecMap.put(leaf2.getId(), leaf2);

        listSpec.resolveReferences(idToSpecMap);

        validateUnresolvedSize("after second resolution", listSpec, 0);
    }

    @Test
    public void testGetInstanceWithValidation() throws Exception {
        leaf1.validate();
        mpicbg.models.CoordinateTransform coordinateTransform = leaf1.getInstance();
        Assert.assertNotNull("transform not created after validation", coordinateTransform);
    }

    @Test
    public void testGetInstanceWithoutValidation() throws Exception {
        mpicbg.models.CoordinateTransform coordinateTransform = leaf1.getInstance();
        Assert.assertNotNull("transform not created prior to validation", coordinateTransform);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWithUnknownClass() throws Exception {
        final LeafTransformSpec spec = new LeafTransformSpec("bad-class", "1 0 0 1 0 0");
        spec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWithNonTransformClass() throws Exception {
        final LeafTransformSpec spec = new LeafTransformSpec(this.getClass().getName(), "1 0 0 1 0 0");
        spec.validate();
    }

    private void validateUnresolvedSize(String context,
                                        TransformSpec spec,
                                        int expectedSize) {
        List<String> unresolvedList = new ArrayList<String>();
        spec.appendUnresolvedIds(unresolvedList);
        Assert.assertEquals("invalid number of unresolved references " + context, expectedSize, unresolvedList.size());
    }

    private static final Logger LOG = LoggerFactory.getLogger(TransformSpecTest.class);
    private static final String AFFINE_2D = AffineModel2D.class.getName();
}
